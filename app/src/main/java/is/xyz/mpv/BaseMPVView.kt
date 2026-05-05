package app.mpvnova.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(context)
        logBundledFfmpegVersion()

        /* set normal options (user-supplied config can override) */
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        initOptions()

        MPVLib.init()

        /* set hardcoded options */
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        MPVLib.setOptionString("force-window", "no")
        // need to idle at least once for playFile() logic to work
        MPVLib.setOptionString("idle", "once")

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

        MPVLib.destroy()
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
        MPVLib.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width == lastSurfaceWidth && height == lastSurfaceHeight)
            return
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        lastSurfaceWidth = -1
        lastSurfaceHeight = -1
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        // detachSurface() assumes libmpv is done using the surface; setting
        // vo=null may not wait for VO deinit on every backend.
        MPVLib.detachSurface()
    }

    private fun logBundledFfmpegVersion() {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir ?: return
        val libavcodec = File(nativeLibDir, "libavcodec.so")
        if (!libavcodec.isFile) {
            Log.w(TAG, "Bundled FFmpeg check skipped: libavcodec.so not found in $nativeLibDir")
            return
        }

        try {
            val marker = "FFmpeg version ".toByteArray(Charsets.ISO_8859_1)
            var result: String? = null
            libavcodec.inputStream().buffered(16384).use { stream ->
                val buf = ByteArray(16384)
                var carry = ByteArray(0)
                while (result == null) {
                    val read = stream.read(buf)
                    if (read == -1) break
                    val chunk = if (carry.isEmpty()) buf.copyOf(read)
                                else carry + buf.copyOf(read)
                    val idx = indexOfBytes(chunk, marker)
                    if (idx >= 0) {
                        var end = idx + marker.size
                        while (end < chunk.size && chunk[end] != 0.toByte()
                            && chunk[end] != '\r'.code.toByte()
                            && chunk[end] != '\n'.code.toByte()) end++
                        result = String(chunk, idx, end - idx, Charsets.ISO_8859_1)
                    } else {
                        carry = if (chunk.size > marker.size)
                            chunk.copyOfRange(chunk.size - marker.size, chunk.size)
                        else chunk
                    }
                }
            }
            if (result != null) {
                Log.i(TAG, "Bundled $result")
            } else {
                Log.w(TAG, "Bundled FFmpeg version string not found in libavcodec.so")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bundled FFmpeg check failed", e)
        }
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        val limit = haystack.size - needle.size
        var i = 0
        outer@ while (i <= limit) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { i++; continue@outer }
            }
            return i
        }
        return -1
    }

    companion object {
        private const val TAG = "mpv"
    }
}
