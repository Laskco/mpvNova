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
import java.io.FileNotFoundException
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun MPVActivity.initMediaSession(): MediaSessionCompat {
    /*
        https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
        https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
        https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
     */
    val session = MediaSessionCompat(this, MPV_ACTIVITY_TAG)
    session.setFlags(0)
    session.setCallback(mediaSessionCallback)
    return session
}

internal fun MPVActivity.updateMediaSession() {
    synchronized (psc) {
        mediaSession?.let { psc.write(it) }
    }
}

internal fun MPVActivity.eventMetadataPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
        "track-list" -> {
            player.loadTracks()
            maybeApplyShieldHi10pFallback()
        }
        "current-tracks/audio/selected", "current-tracks/video/image" -> {
            updateAudioUI()
            maybeApplyShieldHi10pFallback()
        }
        "hwdec-current" -> {
            updateDecoderButton()
            updateGpuNextRetryConfirmation()
        }
    }
    if (metaUpdated)
        updateMetadataDisplay()
}

internal fun MPVActivity.eventBooleanPropertyUi(property: String, value: Boolean) {
    if (!activityIsForeground) return
    when (property) {
        "pause" -> updatePlaybackStatus(value)
        "paused-for-cache" -> {
            streamCacheLoading = value
            refreshLoadingOverlay()
        }
        "mute" -> { // indirectly from updateAudioPresence()
            updateAudioUI()
        }
    }
}

internal fun MPVActivity.eventLongPropertyUi(property: String) {
    if (!activityIsForeground) return
    when (property) {
        "playlist-pos", "playlist-count" -> updatePlaylistButtons()
    }
}

internal fun MPVActivity.eventDoublePropertyUi(property: String) {
    if (!activityIsForeground) return
    when (property) {
        "time-pos/full" -> updatePlaybackTimeline(psc.position)
        "duration/full" -> updatePlaybackDuration(psc.duration)
        "video-params/aspect", "video-params/rotate" -> {
            updateOrientation()
            updatePiPParams()
        }
    }
}

internal fun MPVActivity.eventStringPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
        "speed" -> updateSpeedButton()
        "current-vo" -> {
            updateDecoderButton()
            updateGpuNextRetryConfirmation()
        }
    }
    if (metaUpdated)
        updateMetadataDisplay()
}

internal fun MPVActivity.maybeApplyGpuNextRenderFallback(prefix: String, level: Int, text: String) {
    if (!canApplyGpuNextRenderFallback(level) || !isGpuNextRenderFailure(prefix, text))
        return
    when (gpuNextFallbackAction()) {
        GpuNextFallbackAction.RetryWithCopyHwdec -> retryGpuNextWithCopyHwdec(prefix, text)
        GpuNextFallbackAction.WaitForCopyRetry -> Log.w(
                MPV_ACTIVITY_TAG,
                "Ignoring gpu-next failure log while mediacodec-copy retry is still stabilizing ($prefix: $text)"
            )
        GpuNextFallbackAction.KeepGpuNext -> keepGpuNextAfterRetry(prefix, text)
        GpuNextFallbackAction.FallbackToGpu -> fallbackGpuNextToGpu(prefix, text)
    }
}
