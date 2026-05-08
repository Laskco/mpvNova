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

internal fun MPVActivity.dispatchPointerScrollEvent(ev: MotionEvent): Boolean {
    val horizontal = ev.getAxisValue(MotionEvent.AXIS_HSCROLL)
    val vertical = ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
    val dominant = if (kotlin.math.abs(horizontal) > kotlin.math.abs(vertical)) {
        horizontal
    } else {
        vertical
    }
    if (dominant != 0f) {
        showControls()
        btnSelected = 0
        updateSelectedDpadButton()
        binding.playbackSeekbar.requestFocus()
        val notchCount = kotlin.math.max(1, kotlin.math.abs(dominant).roundToInt())
        val direction = if (dominant < 0f) 1 else -1
        seekPlaybackFromDpad(direction * notchCount * SEEK_DEFAULT_DPAD_STEP_MS)
    }
    return dominant != 0f
}

internal fun MPVActivity.dpadButtons(): List<View> {
    if (binding.controls.visibility != View.VISIBLE || binding.topControls.visibility != View.VISIBLE) {
        return emptyList()
    }
    val views = mutableListOf<View>()
    if (binding.playbackSeekbar.isEnabled) {
        views += binding.playbackSeekbar
    }
    val groups = arrayOf(binding.controlsButtonGroup, binding.topControls)
    for (g in groups) {
        for (i in 0 until g.childCount) {
            val view = g.getChildAt(i)
            if (view.isEnabled && view.isVisible && view.isFocusable) {
                views += view
            }
        }
    }
    return views
}

internal fun MPVActivity.firstControlButtonIndex(controls: List<View>): Int {
    val firstNonSeekbar = controls.indexOfFirst { it !== binding.playbackSeekbar }
    return if (firstNonSeekbar >= 0) firstNonSeekbar else 0
}

internal fun MPVActivity.firstControlButtonView(): View? {
    val groups = arrayOf(binding.controlsButtonGroup, binding.topControls)
    for (group in groups) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.isEnabled && child.isVisible && child.isFocusable) {
                return child
            }
        }
    }
    return null
}

internal fun MPVActivity.interceptDpad(ev: KeyEvent): Boolean {
    val controls = dpadButtons()
    return when {
        btnSelected == -1 && controls.isEmpty() -> interceptDpadWithoutControls(ev)
        controls.isEmpty() -> false
        btnSelected == -1 -> interceptDpadActivation(ev, controls)
        else -> interceptActiveDpad(ev, controls)
    }
}

internal fun MPVActivity.updateSelectedDpadButton() {
    val controls = dpadButtons()
    controls.forEachIndexed { i, child ->
        val selected = i == btnSelected
        if (child.isSelected != selected) {
            child.isSelected = selected
        }
        if (child is ChapterSeekBar) {
            child.setDpadSelected(selected)
        }
        if (!selected && child.isFocused) {
            child.clearFocus()
        }
    }
    controls.getOrNull(btnSelected)?.let { selectedChild ->
        if (selectedChild !== binding.playbackSeekbar && binding.playbackSeekbar.isFocused) {
            binding.playbackSeekbar.clearFocus()
        }
        if (selectedChild.isFocusable && !selectedChild.isFocused) {
            selectedChild.requestFocus()
        }
    }
}

internal fun MPVActivity.interceptKeyDown(event: KeyEvent): Boolean {
    // intercept some keys to provide functionality native to
    // mpvNova even if libmpv already implements these
    var unhandled = 0

    when (event.unicodeChar.toChar()) {
        // (overrides a default binding)
        'j' -> cycleSub()
        '#' -> cycleAudio()

        else -> unhandled++
    }
    // Note: dpad center is bound according to how Android TV apps should generally behave,
    // see <https://developer.android.com/docs/quality-guidelines/tv-app-quality>.
    // Due to implementation inconsistencies enter and numpad enter need to perform the same
    // function (issue #963).
    when (event.keyCode) {
        // (no default binding)
        KeyEvent.KEYCODE_CAPTIONS -> cycleSub()
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
        KeyEvent.KEYCODE_INFO -> toggleControls()
        KeyEvent.KEYCODE_MENU -> openTopMenu()
        KeyEvent.KEYCODE_GUIDE -> openTopMenu()
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> player.cyclePause()

        // (overrides a default binding)
        KeyEvent.KEYCODE_ENTER -> player.cyclePause()

        else -> unhandled++
    }

    return unhandled < 2
}

internal fun MPVActivity.onBackPressedImpl() {
    if (lockedUI)
        return showUnlockControls()

    val notYetPlayed = psc.playlistCount - psc.playlistPos - 1
    if (notYetPlayed <= 0 || !playlistExitWarning) {
        finishWithResult(RESULT_OK, true)
        return
    }

    val restore = pauseForDialog()
    with (AlertDialog.Builder(this)) {
        setMessage(getString(R.string.exit_warning_playlist, notYetPlayed))
        setPositiveButton(R.string.dialog_yes) { dialog, _ ->
            dialog.dismiss()
            finishWithResult(RESULT_OK, true)
        }
        setNegativeButton(R.string.dialog_no) { dialog, _ ->
            dialog.dismiss()
            restore()
        }
        create().show()
    }
}
