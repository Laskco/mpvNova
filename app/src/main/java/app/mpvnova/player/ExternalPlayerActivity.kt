package app.mpvnova.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log

// Thin trampoline for external VIEW intents: launches the real player for a result and relays
// that result back to the caller. It routes by caller package so the player window matches what
// each launcher's result/auto-next handling needs:
//  - Stremio's result-launcher (Compose) breaks if its activity is stopped -> translucent player
//    (TranslucentMPVActivity) so Stremio stays paused, not stopped.
//  - Nuvio (and everyone else) auto-nexts off the full stop->restart lifecycle -> opaque player
//    (MPVActivity) that stops the caller.
// Both player variants build the same result; the trampoline is a lightweight classic Activity
// that reliably relays it.
class ExternalPlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null)
            startPlayer(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startPlayer(intent)
    }

    private fun startPlayer(source: Intent) {
        val caller = source.getStringExtra(EXTRA_EXTERNAL_CALLER_PACKAGE) ?: resolveCallerPackage()
        val playerClass = if (caller == STREMIO_PACKAGE) {
            TranslucentMPVActivity::class.java
        } else {
            ExternalOpaquePlayerActivity::class.java
        }
        Log.v(TAG, "external-player: trampoline caller=$caller -> ${playerClass.simpleName}")
        val playerIntent = Intent(source.action).apply {
            setClass(this@ExternalPlayerActivity, playerClass)
            source.data?.let { data ->
                if (source.type != null)
                    setDataAndType(data, source.type)
                else
                    setData(data)
            }
            source.categories?.forEach { addCategory(it) }
            copyAllowedExtras(source, this)
            materializeContentSubtitles(source, this)
            putExtra(EXTRA_EXTERNAL_PLAYER_RESULT, true)
            caller?.let { putExtra(EXTRA_EXTERNAL_CALLER_PACKAGE, it) }
            if (containsContentUri(EXTERNAL_ALLOWED_EXTRA_KEYS))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivityForResult(playerIntent, REQUEST_PLAYBACK)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Unable to start player for external intent", e)
            setResult(RESULT_CANCELED)
            finish()
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to start player for external intent", e)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun resolveCallerPackage(): String? {
        val referrerPackage = referrer?.androidAppPackageName()
        return callingPackage ?: callingActivity?.packageName ?: referrerPackage
    }

    @Suppress("DEPRECATION")
    private fun copyAllowedExtras(source: Intent, target: Intent) {
        val extras = source.extras ?: return
        for (key in EXTERNAL_ALLOWED_EXTRA_KEYS) {
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
            val resultIntent = buildResultIntent(data)
            resultIntent.clearPermissionFlags()
            if (data != null) {
                Log.v(
                    TAG,
                    "external-player: relay result code=${resultCode.resultName()} " +
                        "action=${resultIntent.action} extras=${resultIntent.resultExtraSummary()}"
                )
            }
            setResult(resultCode, resultIntent)
            finish()
        }
    }

    private fun buildResultIntent(source: Intent?): Intent {
        if (source == null)
            return Intent(RESULT_INTENT)
        return Intent(source.action ?: RESULT_INTENT).apply {
            data = source.data
            copyResultExtras(source, this)
        }
    }

    private fun Intent.clearPermissionFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            removeFlags(RESULT_PERMISSION_FLAGS)
        else
            flags = flags and RESULT_PERMISSION_FLAGS.inv()
    }

    @Suppress("DEPRECATION")
    private fun copyResultExtras(source: Intent, target: Intent) {
        val extras = source.extras ?: return
        for (key in ALLOWED_RESULT_EXTRA_KEYS) {
            if (!extras.containsKey(key))
                continue
            when (val value = extras.get(key)) {
                is Boolean -> target.putExtra(key, value)
                is Int -> target.putExtra(key, value)
                is Long -> target.putExtra(key, value)
                is String -> target.putExtra(key, value)
            }
        }
    }

    companion object {
        private const val TAG = "ExternalPlayerActivity"
        private const val REQUEST_PLAYBACK = 1
        private val ALLOWED_RESULT_EXTRA_KEYS = setOf(
            "duration",
            "end_by",
            "extra_duration",
            "extra_position",
            "extra_uri",
            "position",
            "return_result",
            "url",
        )
        private const val RESULT_PERMISSION_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}

internal fun Int.resultName(): String = when (this) {
    Activity.RESULT_OK -> "RESULT_OK"
    Activity.RESULT_CANCELED -> "RESULT_CANCELED"
    else -> toString()
}

@Suppress("DEPRECATION")
internal fun Intent.resultExtraSummary(): String {
    val extras = extras ?: return "none"
    return extras.keySet()
        .sorted()
        .joinToString(prefix = "[", postfix = "]") { key ->
            val value = if (key in REDACTED_RESULT_EXTRA_KEYS) "<set>" else extras.get(key)
            "$key=$value"
        }
}

private val EXTERNAL_ALLOWED_EXTRA_KEYS = setOf(
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
    "resume_position",
    "secure_uri",
    "skip_segments",
    "startfrom",
    "subs",
    "subs.enable",
    "subs.filename",
    "subs.name",
    "subtitles_location",
    "title",
)
private val REDACTED_RESULT_EXTRA_KEYS = setOf("extra_uri", "url")
internal const val STREMIO_PACKAGE = "com.stremio.one"

private fun Uri.androidAppPackageName(): String? =
    takeIf { scheme == "android-app" }?.host

@Suppress("DEPRECATION")
private fun Intent.containsContentUri(allowedExtraKeys: Set<String>): Boolean {
    val launchExtras = extras
    return data.isContentUri() || (
        launchExtras != null &&
            allowedExtraKeys.any { key ->
                launchExtras.containsKey(key) && launchExtras.get(key).containsContentUri()
            }
    )
}

private fun Any?.containsContentUri(): Boolean {
    return when (this) {
        is Uri -> isContentUri()
        is ArrayList<*> -> any { it.containsContentUri() }
        is Array<*> -> any { it.containsContentUri() }
        else -> false
    }
}

private fun Uri?.isContentUri(): Boolean {
    return this?.scheme == "content"
}
