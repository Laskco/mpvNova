package app.mpvnova.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File
import java.io.IOException

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(
    context: Context,
    attrs: AttributeSet,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        mpvCreate(context)
        BundledFfmpegVersionLogger.log(context)

        /* set normal options (user-supplied config can override) */
        mpvSetOptionString("config", "yes")
        mpvSetOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            mpvSetOptionString(opt, cacheDir)
        initOptions()

        mpvInit()

        /* set hardcoded options */
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        mpvSetOptionString("force-window", "no")
        // need to idle at least once for playFile() logic to work
        mpvSetOptionString("idle", "once")

        holder.addCallback(this)
        observeProperties()
    }

    /**
     * Deinitialize libmpv.
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        // Disable surface callbacks to avoid using uninitialized mpv state
        holder.removeCallback(this)

        mpvDestroy()
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()

    protected abstract fun observeProperties()

    private var filePath: String? = null
    private var lastSurfaceWidth = -1
    private var lastSurfaceHeight = -1

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        mpvSetOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width == lastSurfaceWidth && height == lastSurfaceHeight)
            return
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        mpvSetPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        mpvAttachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        mpvSetOptionString("force-window", "yes")

        if (filePath != null) {
            mpvCommand(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            mpvSetPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        lastSurfaceWidth = -1
        lastSurfaceHeight = -1
        mpvSetPropertyString("vo", "null")
        mpvSetPropertyString("force-window", "no")
        // detachSurface() assumes libmpv is done using the surface; setting
        // vo=null may not wait for VO deinit on every backend.
        mpvDetachSurface()
    }

    companion object {
        private const val TAG = "mpv"
    }
}

private object BundledFfmpegVersionLogger {
    fun log(context: Context) {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir ?: return
        val libavcodec = File(nativeLibDir, "libavcodec.so")
        if (libavcodec.isFile) {
            logVersion(libavcodec)
        } else {
            Log.w(TAG, "Bundled FFmpeg check skipped: libavcodec.so not found in $nativeLibDir")
        }
    }

    private fun logVersion(libavcodec: File) {
        try {
            val result = scanVersion(libavcodec)
            if (result != null) {
                Log.i(TAG, "Bundled $result")
            } else {
                Log.w(TAG, "Bundled FFmpeg version string not found in libavcodec.so")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Bundled FFmpeg check failed", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Bundled FFmpeg check failed", e)
        }
    }

    private fun scanVersion(libavcodec: File): String? {
        val marker = "FFmpeg version ".toByteArray(Charsets.ISO_8859_1)
        var result: String? = null
        libavcodec.inputStream().buffered(FFMPEG_VERSION_SCAN_BUFFER_SIZE).use { stream ->
            val buffer = ByteArray(FFMPEG_VERSION_SCAN_BUFFER_SIZE)
            var carry = ByteArray(0)
            while (result == null) {
                val read = stream.read(buffer)
                if (read == -1) break
                val chunk = versionScanChunk(carry, buffer, read)
                val index = indexOfBytes(chunk, marker)
                if (index >= 0) {
                    result = ffmpegVersionString(chunk, marker, index)
                } else {
                    carry = carryBytes(chunk, marker)
                }
            }
        }
        return result
    }

    private fun versionScanChunk(carry: ByteArray, buffer: ByteArray, read: Int): ByteArray {
        return if (carry.isEmpty()) buffer.copyOf(read) else carry + buffer.copyOf(read)
    }

    private fun carryBytes(chunk: ByteArray, marker: ByteArray): ByteArray {
        return if (chunk.size > marker.size) {
            chunk.copyOfRange(chunk.size - marker.size, chunk.size)
        } else {
            chunk
        }
    }

    private fun ffmpegVersionString(chunk: ByteArray, marker: ByteArray, markerIndex: Int): String {
        var end = markerIndex + marker.size
        while (end < chunk.size && !chunk[end].isVersionLineEnd()) {
            end++
        }
        return String(chunk, markerIndex, end - markerIndex, Charsets.ISO_8859_1)
    }

    private fun Byte.isVersionLineEnd(): Boolean {
        return this == NUL_BYTE || this == CARRIAGE_RETURN_BYTE || this == LINE_FEED_BYTE
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        val limit = haystack.size - needle.size
        var i = 0
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return -1
    }

    private const val TAG = "mpv"
    private const val FFMPEG_VERSION_SCAN_BUFFER_SIZE = 16_384
    private const val NUL_BYTE = 0.toByte()
    private const val CARRIAGE_RETURN_BYTE = '\r'.code.toByte()
    private const val LINE_FEED_BYTE = '\n'.code.toByte()
}
