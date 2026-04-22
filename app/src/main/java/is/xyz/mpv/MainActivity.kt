package app.mpvnova.player

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.File

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        logBundledFfmpegVersion()

        supportActionBar?.setTitle(R.string.mpv_activity)

        if (savedInstanceState == null) {
            with (supportFragmentManager.beginTransaction()) {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, MainScreenFragment())
                commit()
            }
        }
    }

    private fun logBundledFfmpegVersion() {
        val nativeLibDir = applicationInfo.nativeLibraryDir ?: return
        val libavcodec = File(nativeLibDir, "libavcodec.so")
        if (!libavcodec.isFile) {
            Log.w(TAG, "Bundled FFmpeg check skipped: libavcodec.so not found in $nativeLibDir")
            return
        }

        try {
            val text = libavcodec.readBytes().toString(Charsets.ISO_8859_1)
            val match = Regex("""FFmpeg version [^\u0000\r\n]+""").find(text)?.value
            if (match != null) {
                Log.i(TAG, "Bundled $match")
            } else {
                Log.w(TAG, "Bundled FFmpeg version string not found in libavcodec.so")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bundled FFmpeg check failed", e)
        }
    }

    companion object {
        private const val TAG = "mpv"
    }
}
