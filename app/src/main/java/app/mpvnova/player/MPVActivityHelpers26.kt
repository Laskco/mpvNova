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

internal fun MPVActivity.retryGpuNextWithCopyHwdec(prefix: String, text: String) {
    gpuNextRenderFallbackStage = 1
    gpuNextCopyRetryConfirmed = false
    gpuNextCopyRetryDisplayedFrame = false
    Log.w(
        MPV_ACTIVITY_TAG,
        "gpu-next render failure detected, retrying with mediacodec-copy ($prefix: $text)"
    )
    player.fallbackGpuNextToCopyHwdec()
    eventUiHandler.post {
        updateDecoderButton()
        if (activityIsForeground) {
            showToast(
                getString(R.string.pref_gpu_next_title),
                getString(R.string.toast_gpu_next_copy_fallback),
                durationMs = GPU_NEXT_FALLBACK_TOAST_MS
            )
        }
    }
}

internal fun MPVActivity.keepGpuNextAfterRetry(prefix: String, text: String) {
    gpuNextRenderFallbackStage = 2
    Log.w(
        MPV_ACTIVITY_TAG,
        "gpu-next still reports render errors after the HW retry, but keeping " +
            "gpu-next to match stock mpv behavior ($prefix: $text)"
    )
}

internal fun MPVActivity.fallbackGpuNextToGpu(prefix: String, text: String) {
    gpuNextRenderFallbackStage = 2
    Log.w(MPV_ACTIVITY_TAG, "gpu-next render failure detected before HW retry, falling back to gpu ($prefix: $text)")
    player.fallbackGpuNextToGpu()
    eventUiHandler.post {
        updateDecoderButton()
        if (activityIsForeground) {
            showToast(
                getString(R.string.pref_gpu_next_title),
                getString(R.string.toast_gpu_next_fallback),
                durationMs = GPU_NEXT_FALLBACK_TOAST_MS
            )
        }
    }
}

internal fun MPVActivity.isGpuNextRenderFailure(prefix: String, text: String): Boolean {
    val normalizedPrefix = prefix.trim().lowercase(Locale.US)
    val normalizedText = text.trim().lowercase(Locale.US)
    return normalizedPrefix.contains("gpu-next") &&
        GPU_NEXT_RENDER_FAILURE_TEXT.any { normalizedText.contains(it) } ||
        GPU_NEXT_GENERAL_FAILURE_TEXT.any { normalizedText.contains(it) }
}

internal fun MPVActivity.updateGpuNextRetryConfirmation() {
    if (gpuNextRenderFallbackStage != 1 || gpuNextCopyRetryConfirmed)
        return

    val activeVo = player.activeVideoOutput.trim().lowercase(Locale.US)
    val requestedVo = player.requestedVideoOutput.trim().lowercase(Locale.US)
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)

    if (requestedVo.startsWith("gpu-next") &&
        activeVo.startsWith("gpu-next") &&
        activeHwdec == "mediacodec-copy"
    ) {
        gpuNextCopyRetryConfirmed = true
        Log.w(MPV_ACTIVITY_TAG, "Confirmed gpu-next retry is running with mediacodec-copy")
    }
}

internal fun MPVActivity.updateGpuNextRetryFrameConfirmation(prefix: String, text: String) {
    if (gpuNextRenderFallbackStage != 1 || gpuNextCopyRetryDisplayedFrame)
        return

    val normalizedPrefix = prefix.trim().lowercase(Locale.US)
    val normalizedText = text.trim().lowercase(Locale.US)
    val frameShown =
        normalizedPrefix == "cplayer" &&
            (normalizedText.contains("first video frame after restart shown") ||
                normalizedText.contains("playback restart complete"))

    if (frameShown) {
        gpuNextCopyRetryDisplayedFrame = true
        Log.w(MPV_ACTIVITY_TAG, "Confirmed gpu-next retry produced video output")
    }
}

internal fun MPVActivity.fadeGestureText() {
    fadeHandler.removeCallbacks(fadeRunnable3)
    binding.gestureTextView.visibility = View.VISIBLE

    fadeHandler.postDelayed(fadeRunnable3, GESTURE_TEXT_FADE_MS)
}

internal fun MPVActivity.parseControlsTimeout(value: String?): Long {
    return when (value) {
        "never" -> -1L
        else -> value?.toLongOrNull()?.takeIf { it > 0L }
            ?: DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    }
}
