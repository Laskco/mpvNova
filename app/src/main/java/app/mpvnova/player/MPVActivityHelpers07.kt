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

internal fun MPVActivity.onPiPModeChangedImpl(state: Boolean) {
    Log.v(MPV_ACTIVITY_TAG, "onPiPModeChanged($state)")
    if (state) {
        lockedUI = true
        hideControls()
        return
    }

    unlockUI()
    // For whatever stupid reason Android provides no good detection for when PiP is exited
    // so we have to do this shit <https://stackoverflow.com/questions/43174507/#answer-56127742>
    // If we don't exit the activity here it will stick around and not be retrievable from the
    // recents screen, or react to onNewIntent().
    if (activityIsStopped) {
        // Note: On Android 12 or older there's another bug with this: the result will not
        // be delivered to the calling activity and is instead instantly returned the next
        // time, which makes it looks like the file picker is broken.
        finishWithResult(RESULT_OK, true)
    }
}

internal fun MPVActivity.playlistPrev() = mpvCommand(arrayOf("playlist-prev"))

internal fun MPVActivity.playlistNext() = mpvCommand(arrayOf("playlist-next"))

internal fun MPVActivity.showToast(msg: String, cancel: Boolean = false, durationMs: Long = TOAST_UNTITLED_BASE_MS) {
    showToastInternal(null, msg, cancel, durationMs)
}

internal fun MPVActivity.showToast(
    title: String,
    detail: String,
    cancel: Boolean = true,
    durationMs: Long = TOAST_UNTITLED_BASE_MS
) {
    showToastInternal(title, detail, cancel, durationMs)
}

internal fun MPVActivity.showToastInternal(
    title: String?,
    detail: String,
    cancel: Boolean,
    durationMs: Long
) {
    val effectiveDurationMs = resolvedToastDuration(title, detail, durationMs)
    fadeHandler.removeCallbacks(playerToastHideRunnable)
    binding.playerToast.animate().cancel()
    if (cancel) {
        binding.playerToast.alpha = 1f
    }

    binding.playerToastTitle.isVisible = !title.isNullOrBlank()
    binding.playerToastTitle.text = title
    binding.playerToastMessage.text = detail
    updatePlayerToastPlacement()
    binding.playerToast.visibility = View.VISIBLE

    if (binding.playerToast.alpha < 1f) {
        binding.playerToast.alpha = 0f
        binding.playerToast.animate().alpha(1f).setDuration(PLAYER_TOAST_FADE_IN_MS)
    } else {
        binding.playerToast.alpha = 1f
    }

    fadeHandler.postDelayed(playerToastHideRunnable, effectiveDurationMs)
}

internal fun MPVActivity.resolvedToastDuration(
    title: String?,
    detail: String,
    requestedDurationMs: Long
): Long {
    val textLength = (title?.length ?: 0) + detail.length
    return if (!title.isNullOrBlank()) {
        val adaptiveDuration = TOAST_TITLED_BASE_MS +
            (textLength.coerceAtMost(TOAST_TITLED_MAX_CHARS) * TOAST_TITLED_PER_CHAR_MS)
        maxOf(requestedDurationMs, adaptiveDuration.coerceAtMost(TOAST_TITLED_MAX_MS))
    } else {
        val adaptiveDuration = TOAST_UNTITLED_BASE_MS +
            (textLength.coerceAtMost(TOAST_UNTITLED_MAX_CHARS) * TOAST_UNTITLED_PER_CHAR_MS)
        maxOf(requestedDurationMs, adaptiveDuration.coerceAtMost(TOAST_UNTITLED_MAX_MS))
    }
}

internal fun MPVActivity.updatePlayerToastPlacement() {
    val topMarginDp = if (binding.playerTitleOverlay.isVisible) {
        TOAST_TOP_WITH_TITLE_DP
    } else {
        TOAST_TOP_NO_TITLE_DP
    }
    val topMarginPx = Utils.convertDp(activityContext, topMarginDp)
    if ((binding.playerToast.layoutParams as? MarginLayoutParams)?.topMargin == topMarginPx)
        return
    binding.playerToast.updateLayoutParams<MarginLayoutParams> { topMargin = topMarginPx }
}
