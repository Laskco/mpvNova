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

internal fun MPVActivity.controlsShouldBeVisible(): Boolean {
    if (lockedUI)
        return false
    return userIsOperatingSeekbar
}

internal fun MPVActivity.shouldAutoHideControls(): Boolean {
    return controlsDisplayTimeoutMs > 0L &&
            !controlsShouldBeVisible() &&
            !(keepControlsVisibleWhilePaused && psc.pause)
}

internal fun MPVActivity.showControls() {
    if (lockedUI) {
        Log.w(MPV_ACTIVITY_TAG, "cannot show UI in locked mode")
        return
    }

    val controlsWereVisible = binding.controls.visibility == View.VISIBLE
    val controlsNeedAlphaReset = !controlsWereVisible ||
            fadeRunnable.hasStarted ||
            binding.controls.alpha < 1f ||
            binding.topControls.alpha < 1f ||
            binding.playerTitleOverlay.alpha < 1f ||
            binding.controlsScrim.alpha < 1f

    fadeHandler.removeCallbacks(fadeRunnable)
    if (controlsNeedAlphaReset) {
        binding.controls.animate().setListener(null).cancel()
        binding.topControls.animate().setListener(null).cancel()
        binding.playerTitleOverlay.animate().setListener(null).cancel()

        binding.controls.alpha = 1f
        binding.topControls.alpha = 1f
        binding.playerTitleOverlay.alpha = 1f
        binding.controlsScrim.alpha = 1f
        fadeRunnable.hasStarted = false
    }

    if (!controlsWereVisible) {
        binding.controls.setVisibilityIfChanged(View.VISIBLE)
        binding.topControls.setVisibilityIfChanged(View.VISIBLE)
        binding.controlsScrim.setVisibilityIfChanged(View.VISIBLE)
        binding.timeInfoPanel.setVisibilityIfChanged(View.VISIBLE)
        updatePlayerTitleOverlay()

        if (this.statsFPS) {
            updateStats()
            binding.statsTextView.setVisibilityIfChanged(View.VISIBLE)
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.navigationBars())

        updatePlaybackTimeline(psc.position, forceTextUpdate = true)
        updatePlayerToastPlacement()
        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)
    }

    updateClockInfo(force = !controlsWereVisible)

    if (btnSelected != -1) {
        binding.controls.post {
            if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                updateSelectedDpadButton()
            }
        }
    }

    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

internal fun MPVActivity.hideControls() {
    if (controlsShouldBeVisible())
        return
    if (btnSelected != -1) {
        btnSelected = -1
        updateSelectedDpadButton()
    }
    binding.playbackSeekbar.clearFocus()
    // use GONE here instead of INVISIBLE (which makes more sense) because of Android bug with surface views
    // see http://stackoverflow.com/a/12655713/2606891
    binding.controls.setVisibilityIfChanged(View.GONE)
    binding.topControls.setVisibilityIfChanged(View.GONE)
    binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
    binding.controlsScrim.setVisibilityIfChanged(View.GONE)
    binding.timeInfoPanel.setVisibilityIfChanged(View.GONE)
    binding.statsTextView.setVisibilityIfChanged(View.GONE)
    updatePlayerToastPlacement()
    clockHandler.removeCallbacks(clockRunnable)

    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
}

internal fun MPVActivity.hideControlsFade() {
    fadeHandler.removeCallbacks(fadeRunnable)
    fadeHandler.post(fadeRunnable)
}

internal fun MPVActivity.toggleControls(): Boolean {
    return if (lockedUI) {
        false
    } else if (controlsShouldBeVisible()) {
        true
    } else if (binding.controls.visibility == View.VISIBLE && !fadeRunnable.hasStarted) {
            hideControlsFade()
            false
    } else {
        showControls()
        true
    }
}

internal fun MPVActivity.showUnlockControls() {
    fadeHandler.removeCallbacks(fadeRunnable2)
    binding.unlockBtn.animate().setListener(null).cancel()

    binding.unlockBtn.alpha = 1f
    binding.unlockBtn.visibility = View.VISIBLE

    fadeHandler.postDelayed(fadeRunnable2, DEFAULT_CONTROLS_DISPLAY_TIMEOUT)
}

internal fun MPVActivity.dispatchPointerMotionEvent(ev: MotionEvent): Boolean {
    val scrollHandled = ev.actionMasked == MotionEvent.ACTION_SCROLL &&
        dispatchPointerScrollEvent(ev)
    val pointerHandled = scrollHandled || player.onPointerEvent(ev)
    if (!pointerHandled && ev.actionMasked == MotionEvent.ACTION_HOVER_MOVE)
        showControls()
    return pointerHandled
}
