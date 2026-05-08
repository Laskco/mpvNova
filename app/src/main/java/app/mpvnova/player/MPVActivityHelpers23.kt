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

internal fun MPVActivity.updatePlaybackDuration(durationMs: Long) {
    val duration = (durationMs / MPV_MILLIS_PER_SECOND_FLOAT).roundToInt()
    if (!useTimeRemaining) {
        val durationText = Utils.prettyTime(duration)
        binding.playbackDurationTxt.setTextIfChanged(durationText)
    }

    val seekbarMax = seekbarProgressFromMillis(durationMs)
    val seekbarMaxChanged = !userIsOperatingSeekbar && binding.playbackSeekbar.max != seekbarMax
    if (seekbarMaxChanged)
        binding.playbackSeekbar.max = seekbarMax
    if (duration > 0 && seekbarMaxChanged)
        updateChapterMarkers()
    if (binding.timeInfoPanel.visibility == View.VISIBLE)
        updateClockInfo()
}

internal fun MPVActivity.updatePlaybackStatus(paused: Boolean) {
    val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
    if (lastPlayButtonIconRes != r) {
        binding.playBtn.setImageResource(r)
        lastPlayButtonIconRes = r
    }

    updatePiPParams()
    if (paused) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (keepControlsVisibleWhilePaused && binding.controls.visibility == View.VISIBLE)
            fadeHandler.removeCallbacks(fadeRunnable)
        else if (keepControlsVisibleWhilePaused)
            showControls()
    } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (controlsDisplayTimeoutMs > 0L && binding.controls.visibility == View.VISIBLE) {
            fadeHandler.removeCallbacks(fadeRunnable)
            fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
        }
    }
}

internal fun MPVActivity.updateDecoderButton() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        eventUiHandler.post { updateDecoderButton() }
        return
    }
    val decoderText = when (player.currentDecoderMode) {
        MPVView.DECODER_MODE_HW_PLUS -> "HW+"
        MPVView.DECODER_MODE_HW -> "HW"
        MPVView.DECODER_MODE_GNEXT, MPVView.DECODER_MODE_SHIELD_H10P -> currentGpuNextBadge()
        MPVView.DECODER_MODE_SW -> "SW"
        else -> "HW"
    }
    binding.cycleDecoderBtn.setTextIfChanged(decoderText)
}

internal fun MPVActivity.toggleStatsOverlay() {
    mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle"))
}

internal fun MPVActivity.maybeApplyShieldHi10pFallback() {
    val shouldFallback = autoDecoderFallback &&
        isNvidiaShieldDevice() &&
        player.isHi10pH264Video() &&
        player.currentDecoderMode in arrayOf(MPVView.DECODER_MODE_HW, MPVView.DECODER_MODE_HW_PLUS)
    if (shouldFallback) {
        player.applyDecoderMode(MPVView.DECODER_MODE_SHIELD_H10P)
        updateDecoderButton()
    }
}

internal fun MPVActivity.applySessionDecoderModeIfNeeded() {
    val mode = sessionDecoderMode ?: preferredDecoderMode.takeIf {
        !autoDecoderFallback && it.isNotBlank()
    } ?: return
    player.applyDecoderMode(mode)
    updateDecoderButton()
}

internal fun MPVActivity.isNvidiaShieldDevice(): Boolean {
    return Build.MANUFACTURER.contains("NVIDIA", ignoreCase = true) ||
            Build.MODEL.contains("SHIELD", ignoreCase = true) ||
            Build.PRODUCT.contains("shield", ignoreCase = true)
}

internal fun MPVActivity.updateSpeedButton() {
    binding.cycleSpeedBtn.setTextIfChanged(getString(R.string.ui_speed, psc.speed))
}
