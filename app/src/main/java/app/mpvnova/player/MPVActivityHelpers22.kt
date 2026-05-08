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

internal fun MPVActivity.updatePlayerTitleWidth() {
    val horizontalMargin = Utils.convertDp(activityContext, PLAYER_TITLE_HORIZONTAL_MARGIN_DP)
    val width = resources.displayMetrics.widthPixels
    val maxWidth = (width - horizontalMargin * 2)
        .coerceAtLeast(Utils.convertDp(activityContext, PLAYER_TITLE_MIN_WIDTH_DP))
    val cappedWidth = minOf(maxWidth, Utils.convertDp(activityContext, PLAYER_TITLE_MAX_WIDTH_DP))
    if (binding.playerTitlePrimary.maxWidth != cappedWidth)
        binding.playerTitlePrimary.maxWidth = cappedWidth
}

internal fun MPVActivity.seekbarProgressFromMillis(positionMs: Long): Int {
    val scaled = positionMs.coerceAtLeast(0L) * SEEK_BAR_PRECISION / MILLIS_PER_SECOND_LONG
    return scaled.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun MPVActivity.millisFromSeekbarProgress(progress: Int): Long {
    return progress.toLong() * MILLIS_PER_SECOND_LONG / SEEK_BAR_PRECISION
}

internal fun MPVActivity.seekPlaybackFromDpad(deltaMs: Long) {
    val durationMs = psc.duration.coerceAtLeast(0L)
    if (durationMs <= 0L)
        return
    val currentPositionMs = psc.position.coerceAtLeast(0L)
    val newPositionMs = (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
    player.timePos = newPositionMs / MPV_MILLIS_PER_SECOND_DOUBLE
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(newPositionMs))
    updatePlaybackTimeline(newPositionMs, forceTextUpdate = true)
}

internal fun MPVActivity.seekDeltaFromDpadEvent(ev: KeyEvent): Long {
    val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1L else -1L
    val magnitudeMs = when {
        ev.repeatCount >= SEEK_FAST_REPEAT_THRESHOLD -> SEEK_FAST_STEP_MS
        ev.repeatCount >= SEEK_MEDIUM_REPEAT_THRESHOLD -> SEEK_MEDIUM_STEP_MS
        ev.repeatCount >= SEEK_SLOW_REPEAT_THRESHOLD -> SEEK_SLOW_STEP_MS
        else -> SEEK_DEFAULT_DPAD_STEP_MS
    }
    return direction * magnitudeMs
}

internal fun MPVActivity.setPlaybackSeekbarProgress(progress: Int) {
    if (binding.playbackSeekbar.progress != progress)
        binding.playbackSeekbar.progress = progress
    lastSeekbarProgress = progress
    lastSeekbarUiUpdateMs = SystemClock.uptimeMillis()
}

internal fun MPVActivity.updatePlaybackTimeline(positionMs: Long, forceTextUpdate: Boolean = false) {
    if (!userIsOperatingSeekbar) {
        val progress = seekbarProgressFromMillis(positionMs)
        val now = SystemClock.uptimeMillis()
        val shouldUpdateSeekbar = forceTextUpdate ||
                progress == 0 ||
                progress == binding.playbackSeekbar.max ||
                now - lastSeekbarUiUpdateMs >= PLAYER_SEEKBAR_UI_INTERVAL_MS
        if (shouldUpdateSeekbar && progress != lastSeekbarProgress)
            setPlaybackSeekbarProgress(progress)
    }
    updatePlaybackText((positionMs / MILLIS_PER_SECOND_LONG).toInt().coerceAtLeast(0), force = forceTextUpdate)
}

internal fun MPVActivity.updatePlaybackText(position: Int, force: Boolean = false) {
    if (!force && lastDisplayedPlaybackSecond == position)
        return
    lastDisplayedPlaybackSecond = position
    binding.playbackPositionTxt.setTextIfChanged(Utils.prettyTime(position))
    if (useTimeRemaining) {
        val diff = psc.durationSec - position
        val durationText = if (diff <= 0)
            "-00:00"
        else
            Utils.prettyTime(-diff, true)
        binding.playbackDurationTxt.setTextIfChanged(durationText)
    }

    // Keep the expensive secondary UI work at roughly once per second even
    // though the seekbar itself now updates with full playback precision.
    updateStats()
    if (binding.timeInfoPanel.visibility == View.VISIBLE)
        updateClockInfo()
}
