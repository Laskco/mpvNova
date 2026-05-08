package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import app.mpvnova.player.MpvEvent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import androidx.appcompat.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Rational
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun MPVActivity.prepareMediaTitleFromIntent(intent: Intent?, filepath: String?) {
    pendingItemTitle = titleFromIntentExtras(intent) ?: VlcTitleResolver.queryTitleFromPathLike(filepath)
    pendingFileName = VlcTitleResolver.fileNameFromPathLike(filepath)
}

internal fun MPVActivity.titleFromIntentExtras(intent: Intent?): String? {
    val extras = intent?.extras ?: return null
    return VLC_TITLE_EXTRA_KEYS.firstNotNullOfOrNull { key ->
        val title = if (extras.containsKey(key)) {
            extras.getString(key) ?: extras.getCharSequence(key)?.toString()
        } else {
            null
        }
        title?.let(VlcTitleResolver::itemTitleFromExtra)
    }
}

internal fun MPVActivity.resolveVlcStyleVideoTitle(): String? {
    currentItemTitle?.let { return it }
    val path = currentMpvPath()
    val fileName = pendingFileName ?: VlcTitleResolver.fileNameFromPathLike(path)
    return VlcTitleResolver.resolve(
        itemTitle = null,
        mediaTitle = psc.meta.mediaTitle,
        fileName = fileName,
        isStream = isNetworkStreamPath(path)
    )
}

internal fun MPVActivity.parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.let { resolveUri(it) }
        Intent.ACTION_SEND -> pathFromSendIntent(intent)
        Intent.ACTION_SEND_MULTIPLE -> pathFromMultipleSendIntent(intent)
        else -> intent.getStringExtra("filepath")
    }
}

internal fun MPVActivity.resolveUri(data: Uri): String? {
    val filepath = when (data.scheme) {
        "file" -> data.path
        "content" -> translateContentUri(data)
        // mpv supports data URIs but needs data:// to pass it through correctly
        "data" -> "data://${data.schemeSpecificPart}"
        "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh",
        "tcp", "udp", "lavf", "ftp"
        -> data.toString()
        else -> null
    }

    if (filepath == null)
        Log.e(MPV_ACTIVITY_TAG, "unknown scheme: ${data.scheme}")
    return filepath
}

internal fun MPVActivity.translateContentUri(uri: Uri): String {
    Log.v(MPV_ACTIVITY_TAG, "Resolving content URI: $uri")
    return uri.toString()
}

internal fun MPVActivity.parseIntentExtras(extras: Bundle?) {
    onloadCommands.clear()
    val launchExtras = extras ?: Bundle.EMPTY

    if (resumeIdentityFromSource(currentResumeSource) != null)
        addOnloadOption("resume-playback", "no")

    // Note: these only apply to the first file, it's not clear what the semantics for a
    // playlist should be.
    if (launchExtras.getByte("decode_mode") == 2.toByte())
        addOnloadOption("hwdec", "no")

    addIntentSubtitles(launchExtras)
    applyIntentStartPosition(launchExtras)
}

internal fun MPVActivity.trackSwitchNotification(f: () -> TrackData) {
    val (track_id, track_type) = f()
    val trackPrefix = when (track_type) {
        "audio" -> getString(R.string.track_audio)
        "sub"   -> getString(R.string.track_subs)
        "video" -> "Video"
        else    -> "???"
    }

    val detail = if (track_id == -1) {
        getString(R.string.track_off)
    } else {
        player.tracks[track_type]?.firstOrNull{ it.mpvId == track_id }?.name ?: "???"
    }
    showToast(trackPrefix, detail, true)
}
