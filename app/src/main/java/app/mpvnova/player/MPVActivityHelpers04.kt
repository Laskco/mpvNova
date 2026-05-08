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

internal fun MPVActivity.formatResumeTime(ms: Long): String =
    Utils.prettyTime((ms / MILLIS_PER_SECOND_LONG).toInt())

/**
 * Requests or abandons audio focus and noisy receiver depending on the playback state.
 * @warning Call from event thread, not UI thread
 */

internal fun MPVActivity.handleAudioFocus() {
    if ((psc.pause && !psc.cachePause) || !isPlayingAudio) {
        if (becomingNoisyReceiverRegistered)
            unregisterReceiver(becomingNoisyReceiver)
        becomingNoisyReceiverRegistered = false
    } else {
        if (!becomingNoisyReceiverRegistered)
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        becomingNoisyReceiverRegistered = true
        // (re-)request audio focus
        // Note that this will actually request focus every time the user unpauses, refer to discussion in #1066
        if (requestAudioFocus()) {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, "request")
        } else {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "request")
        }
    }
}

internal fun MPVActivity.requestAudioFocus(): Boolean {
    val manager = audioManager
    val req = audioFocusRequest ?:
        with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
        setAudioAttributes(with(AudioAttributesCompat.Builder()) {
            // N.B.: libmpv may use different values in ao_audiotrack, but here we always pretend to be music.
            setUsage(AudioAttributesCompat.USAGE_MEDIA)
            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            build()
        })
        setOnAudioFocusChangeListener {
            onAudioFocusChange(it, "callback")
        }
        build()
    }
    val res = manager?.let { AudioManagerCompat.requestAudioFocus(it, req) }
    return if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        audioFocusRequest = req
        true
    } else {
        false
    }
}

internal fun MPVActivity.onAudioFocusChange(type: Int, source: String) {
    Log.v(MPV_ACTIVITY_TAG, "Audio focus changed: $type ($source)")
    if (ignoreAudioFocus || isFinishing)
        return
    when (type) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // loss can occur in addition to ducking, so remember the old callback
            val oldRestore = audioFocusRestore
            val wasPlayerPaused = player.paused ?: false
            player.paused = true
            audioFocusRestore = {
                oldRestore()
                if (!wasPlayerPaused) player.paused = false
            }
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            mpvCommand(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
            audioFocusRestore = {
                val inv = 1f / AUDIO_FOCUS_DUCKING
                mpvCommand(arrayOf("multiply", "volume", inv.toString()))
            }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
            audioFocusRestore()
            audioFocusRestore = {}
        }
    }
}

internal fun MPVActivity.keepPlaybackForDialog(): StateRestoreCallback {
    val oldValue = mpvGetPropertyString("keep-open")
    mpvSetPropertyBoolean("keep-open", true)
    return {
        oldValue?.also { mpvSetPropertyString("keep-open", it) }
    }
}

internal fun MPVActivity.pauseForDialog(): StateRestoreCallback {
    val useKeepOpen = when (noUIPauseMode) {
        "always" -> true
        "audio-only" -> isPlayingAudioOnly()
        else -> false // "never"
    }
    if (useKeepOpen) {
        // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
        return keepPlaybackForDialog()
    }

    // Pause playback during UI dialogs
    val wasPlayerPaused = player.paused ?: true
    player.paused = true
    return {
        if (!wasPlayerPaused)
            player.paused = false
    }
}

internal fun MPVActivity.updateStats() {
    if (!statsFPS)
        return
    val statsText = getString(R.string.ui_fps, player.estimatedVfFps)
    if (binding.statsTextView.text.toString() != statsText)
        binding.statsTextView.text = statsText
}

internal fun MPVActivity.updateClockInfo(force: Boolean = false) {
    val now = System.currentTimeMillis()
    val tick = now / MILLIS_PER_SECOND_LONG
    if (!force && lastClockInfoTick == tick)
        return
    lastClockInfoTick = tick

    val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
    if (clockFormatter == null || clockFormatterIs24 != is24Hour) {
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        clockFormatter = SimpleDateFormat(pattern, Locale.getDefault())
        clockFormatterIs24 = is24Hour
    }
    val clockText = clockFormatter!!.format(Date(now))
    if (binding.clockTextView.text.toString() != clockText)
        binding.clockTextView.text = clockText

    val remainingSeconds = (psc.durationSec - psc.positionSec).coerceAtLeast(0)
    if (psc.durationSec > 0 && remainingSeconds > 0) {
        val endTimeMillis = now + (remainingSeconds * MILLIS_PER_SECOND_LONG)
        val endsAtText = getString(
            R.string.player_ends_at,
            clockFormatter!!.format(Date(endTimeMillis))
        )
        binding.endsAtTextView.visibility = View.VISIBLE
        if (binding.endsAtTextView.text.toString() != endsAtText)
            binding.endsAtTextView.text = endsAtText
    } else {
        binding.endsAtTextView.visibility = View.GONE
    }
}
