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

internal fun MPVActivity.setTrackForMemory(
    type: String, mpvId: Int, title: String, score: Double, exact: Boolean
) {
    when (type) {
        "sub"   -> player.sid = mpvId
        "audio" -> player.aid = mpvId
    }
    Log.v(
        MPV_ACTIVITY_TAG,
        "track-memory: restored $type track #$mpvId " +
                "(title='$title', exact=$exact, score=$score)"
    )
}

internal fun MPVActivity.trackMemoryKeys(type: String): Pair<String, String> = when (type) {
    "sub"   -> "last_user_sub_title" to "last_user_sub_lang"
    "audio" -> "last_user_audio_title" to "last_user_audio_lang"
    else    -> throw IllegalArgumentException("unknown track type: $type")
}

internal fun MPVActivity.currentSubScaleState(): MediaPickerDialog.ValueState {
    val maxLevel = subScaleSteps.lastIndex
    return MediaPickerDialog.ValueState(
        label = getSubScaleLabel(),
        active = isSubScaleOn(),
        canDecrease = subScaleLevel > 0,
        canIncrease = subScaleLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSubPosState(): MediaPickerDialog.ValueState {
    val maxLevel = subPosSteps.lastIndex
    return MediaPickerDialog.ValueState(
        label = getSubPosLabel(),
        active = isSubPosOn(),
        canDecrease = subPosLevel > 0,
        canIncrease = subPosLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSecondaryPosState(): MediaPickerDialog.ValueState {
    val maxLevel = secondaryPosSteps.lastIndex
    // Secondary subs only render when a secondary track is on, so the
    // position controls are useless otherwise — dim and disable them
    // until the user actually enables a secondary track.
    val secondaryOn = player.secondarySid != -1
    return MediaPickerDialog.ValueState(
        label = getSecondaryPosLabel(),
        active = isSecondaryPosOn() && secondaryOn,
        enabled = secondaryOn,
        canDecrease = secondaryOn && secondaryPosLevel > 0,
        canIncrease = secondaryOn && secondaryPosLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSecondarySubState(): MediaPickerDialog.ValueState {
    val available = availableSecondarySubTracks()
    if (available.isEmpty()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.sub_secondary_unavailable),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false,
        )
    }
    val currentSid = player.secondarySid
    val on = currentSid != -1 && available.any { it.mpvId == currentSid }
    return MediaPickerDialog.ValueState(
        label = if (on) "#$currentSid" else getString(R.string.status_off),
        active = on,
        // +/- now cycles through Off → every available track → back to Off,
        // so both directions are always available when there's at least
        // one alternate track to choose from.
        canDecrease = true,
        canIncrease = true,
    )
}

internal fun MPVActivity.currentSubFilterStates(): MediaPickerDialog.SubFilterStates {
    return MediaPickerDialog.SubFilterStates(
        subScale = currentSubScaleState(),
        subPos = currentSubPosState(),
        secondaryPos = currentSecondaryPosState(),
        secondarySub = currentSecondarySubState(),
    )
}

internal fun MPVActivity.applySubScaleProperty() {
    mpvSetPropertyDouble("sub-scale", subScaleSteps[subScaleLevel])
}
