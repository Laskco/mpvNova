package app.mpvnova.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

class ExternalPlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null)
            startPlayer(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null)
            startPlayer(intent)
    }

    private fun startPlayer(source: Intent) {
        // Keep caller extras intact, but force playback into our own activity.
        val playerIntent = Intent(source.action).apply {
            setClass(this@ExternalPlayerActivity, MPVActivity::class.java)
            source.data?.let { data ->
                if (source.type != null)
                    setDataAndType(data, source.type)
                else
                    setData(data)
            }
            source.categories?.forEach { addCategory(it) }
            copyAllowedExtras(source, this)
            flags = source.flags and FORWARDED_FLAGS
        }

        try {
            startActivityForResult(playerIntent, REQUEST_PLAYBACK)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start player for external intent", e)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun copyAllowedExtras(source: Intent, target: Intent) {
        val extras = source.extras ?: return
        for (key in ALLOWED_EXTRA_KEYS) {
            if (!extras.containsKey(key))
                continue
            when (val value = extras.get(key)) {
                null -> target.putExtra(key, null as String?)
                is Boolean -> target.putExtra(key, value)
                is Byte -> target.putExtra(key, value)
                is Int -> target.putExtra(key, value)
                is Long -> target.putExtra(key, value)
                is String -> target.putExtra(key, value)
                is Uri -> target.putExtra(key, value)
                is ArrayList<*> -> copyArrayListExtra(key, value, target)
                is Array<*> -> copyArrayExtra(key, value, target)
            }
        }
    }

    private fun copyArrayListExtra(key: String, value: ArrayList<*>, target: Intent) {
        if (value.all { it is Uri })
            target.putParcelableArrayListExtra(key, ArrayList(value.filterIsInstance<Uri>()))
    }

    private fun copyArrayExtra(key: String, value: Array<*>, target: Intent) {
        when {
            value.all { it is String } ->
                target.putExtra(key, value.filterIsInstance<String>().toTypedArray())
            value.all { it is Uri } ->
                target.putExtra(key, value.filterIsInstance<Uri>().toTypedArray())
        }
    }

    @Deprecated("Deprecated in Android API, but still required for legacy external-player callers.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PLAYBACK) {
            setResult(resultCode, data?.let { buildResultIntent(it) })
            finish()
        }
    }

    private fun buildResultIntent(source: Intent): Intent {
        return Intent(source.action).apply {
            data = source.data
            source.type?.let { type = it }
            copyAllowedExtras(source, this)
        }
    }

    companion object {
        private const val TAG = "ExternalPlayerActivity"
        private const val REQUEST_PLAYBACK = 1
        private val ALLOWED_EXTRA_KEYS = setOf(
            Intent.EXTRA_STREAM,
            Intent.EXTRA_TEXT,
            "decode_mode",
            "duration",
            "end_by",
            "extra_duration",
            "extra_position",
            "extra_uri",
            "from_start",
            "item_location",
            "position",
            "return_result",
            "secure_uri",
            "subs",
            "subs.enable",
            "subs.filename",
            "subs.name",
            "subtitles_location",
            "title",
        )
        private const val FORWARDED_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
