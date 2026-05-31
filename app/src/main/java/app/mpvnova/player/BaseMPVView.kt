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

    private var voInUse: String? = null
    private var detachedVoInUse: String? = null

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String?) {
        voInUse = vo
        detachedVoInUse = null
        if (vo != null)
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

        val pendingFilePath = filePath
        if (pendingFilePath != null) {
            mpvCommand(arrayOf("loadfile", pendingFilePath))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            (voInUse ?: detachedVoInUse)?.let { mpvSetPropertyString("vo", it) }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        lastSurfaceWidth = -1
        lastSurfaceHeight = -1
        if (voInUse == null)
            detachedVoInUse = currentVoForSurfaceRestore()
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

private fun currentVoForSurfaceRestore(): String? {
    return mpvGetPropertyString("current-vo")
        ?.takeIf { it.isValidVoForRestore() }
        ?: mpvGetPropertyString("options/vo")
            ?.takeIf { it.isValidVoForRestore() }
}

private fun String.isValidVoForRestore(): Boolean {
    val value = trim()
    return value.isNotEmpty() && value != "null"
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
            val result = NativeLibraryVersion.find(libavcodec, "FFmpeg version ")
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

    private const val TAG = "mpv"
}
