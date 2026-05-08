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

@SuppressLint("ClickableViewAccessibility")
internal fun MPVActivity.initListeners() {
    bindClickListeners()
    bindLongClickListeners()
    bindSeekbarListeners()
    bindTouchAndInsetsListeners()
    bindActivityCallbacks()
}

internal fun MPVActivity.finishWithResult(code: Int, includeTimePos: Boolean = false) {
    if (isFinishing) // only count first call
        return
    val result = Intent(RESULT_INTENT)
    result.data = if (intent.data?.scheme == "file") null else intent.data
    if (includeTimePos) {
        if (eofWasReached) {
            result.putExtra("end_by", "playback_completion")
        } else {
            val safePosition = psc.position.coerceAtLeast(0L)
            val safeDuration = psc.duration.coerceAtLeast(0L)
            result.putExtra("position", safePosition.toInt())
            result.putExtra("duration", safeDuration.toInt())
            result.putExtra("extra_position", safePosition)
            result.putExtra("extra_duration", safeDuration)
            intent.data?.takeUnless { it.scheme == "file" }?.let {
                result.putExtra("extra_uri", it.toString())
            }
            result.putExtra("end_by", "user")
        }
    }
    setResult(code, result)
    finish()
}

internal fun MPVActivity.resetPlaybackResultState() {
    playbackHasStarted = false
    eofWasReached = false
}

internal fun MPVActivity.isNetworkStreamPath(path: String?): Boolean {
    val normalized = path?.trim()?.lowercase(Locale.US) ?: return false
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

internal fun MPVActivity.currentMpvPath(): String? {
    return mpvGetPropertyString("stream-open-filename")
        ?: mpvGetPropertyString("path")
        ?: mpvGetPropertyString("filename")
}

internal fun MPVActivity.prepareStreamLoading(path: String?) {
    streamOpenLoading = isNetworkStreamPath(path)
    streamCacheLoading = false
    refreshLoadingOverlay()
}

internal fun MPVActivity.refreshLoadingOverlay() {
    val visible = streamOpenLoading || streamCacheLoading
    binding.loadingText.setText(
        if (streamCacheLoading) R.string.player_buffering_stream
        else R.string.player_loading_stream
    )
    binding.loadingOverlay.animate().cancel()
    if (visible) {
        if (binding.loadingOverlay.visibility != View.VISIBLE) {
            binding.loadingOverlay.alpha = 0f
            binding.loadingOverlay.visibility = View.VISIBLE
        }
        binding.loadingOverlay.animate()
            .alpha(1f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .setListener(null)
    } else if (binding.loadingOverlay.visibility == View.VISIBLE) {
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .withEndAction { binding.loadingOverlay.visibility = View.GONE }
    }
}

internal fun MPVActivity.updateAudioPresence() {
    val haveAudio = mpvGetPropertyBoolean("current-tracks/audio/selected")
    if (haveAudio == null) {
        // If we *don't know* if there's an active audio track then don't update to avoid
        // spurious UI changes. The property will become available again later.
        return
    }
    isPlayingAudio = (haveAudio && mpvGetPropertyBoolean("mute") != true)
}
