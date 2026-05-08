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

internal fun MPVActivity.applySubPosProperty() {
    mpvSetPropertyInt("sub-pos", subPosSteps[subPosLevel])
}

internal fun MPVActivity.applySecondaryPosProperty() {
    mpvSetPropertyInt("secondary-sub-pos", secondaryPosSteps[secondaryPosLevel])
}

internal fun MPVActivity.adjustSubScale(delta: Int): MediaPickerDialog.ValueState {
    val maxLevel = subScaleSteps.lastIndex
    subScaleLevel = (subScaleLevel + delta).coerceIn(0, maxLevel)
    applySubScaleProperty()
    writeSettings()
    showToast(
        getString(R.string.btn_sub_scale),
        getSubScaleLabel()
    )
    return currentSubScaleState()
}

internal fun MPVActivity.adjustSubPos(delta: Int): MediaPickerDialog.ValueState {
    val maxLevel = subPosSteps.lastIndex
    subPosLevel = (subPosLevel + delta).coerceIn(0, maxLevel)
    applySubPosProperty()
    writeSettings()
    showToast(getString(R.string.btn_sub_pos), getSubPosLabel())
    return currentSubPosState()
}

internal fun MPVActivity.adjustSecondaryPos(delta: Int): MediaPickerDialog.ValueState {
    // Defensive: should be greyed out by canDecrease/canIncrease but the
    // value would be meaningless without a secondary track on screen.
    if (player.secondarySid == -1) return currentSecondaryPosState()
    val maxLevel = secondaryPosSteps.lastIndex
    secondaryPosLevel = (secondaryPosLevel + delta).coerceIn(0, maxLevel)
    applySecondaryPosProperty()
    writeSettings()
    showToast(getString(R.string.btn_secondary_pos), getSecondaryPosLabel())
    return currentSecondaryPosState()
}

internal fun MPVActivity.adjustSecondarySub(delta: Int): MediaPickerDialog.ValueState {
    val available = availableSecondarySubTracks()
    if (available.isEmpty()) {
        return currentSecondarySubState()
    }
    // Cycle through: Off → track1 → track2 → ... → trackN → Off → ...
    // -1 represents the Off slot in this cycle. This lets the user step
    // forward/backward through every non-primary track instead of being
    // stuck with whatever mpv auto-picked when secondary first turned on.
    val cycle = listOf(-1) + available.map { it.mpvId }
    val current = player.secondarySid
    val currentIdx = cycle.indexOf(current).let { if (it < 0) 0 else it }
    // Modular arithmetic that handles negative deltas correctly.
    val step = if (delta == 0) 0 else delta
    val nextIdx = ((currentIdx + step) % cycle.size + cycle.size) % cycle.size
    val nextSid = cycle[nextIdx]
    player.secondarySid = nextSid

    val toastValue = if (nextSid == -1) {
        getString(R.string.status_off)
    } else {
        // Use the friendly track name in the toast so the user can tell
        // which language they just landed on, rather than just an id.
        available.firstOrNull { it.mpvId == nextSid }?.name ?: "#$nextSid"
    }
    showToast(getString(R.string.btn_secondary_sub), toastValue)
    return currentSecondarySubState()
}

internal fun MPVActivity.swapPrimaryAndSecondarySub() {
    val primary = player.sid
    val secondary = player.secondarySid
    // Nothing meaningful to swap if there's no secondary track active.
    if (secondary == -1) return
    // Clear secondary first so mpv doesn't briefly see the same track set
    // as both primary and secondary (it auto-rejects that state).
    player.secondarySid = -1
    player.sid = secondary
    if (primary != -1) {
        player.secondarySid = primary
    }
    showToast(
        getString(R.string.btn_secondary_sub),
        getString(R.string.status_swapped)
    )
}

internal fun MPVActivity.applySavedSubFilterDefaults() {
    if (!persistSubFilters) return
    mpvSetOptionString("sub-scale", subScaleSteps[subScaleLevel].toString())
    mpvSetOptionString("sub-pos", subPosSteps[subPosLevel].toString())
    mpvSetOptionString("secondary-sub-pos", secondaryPosSteps[secondaryPosLevel].toString())
}
