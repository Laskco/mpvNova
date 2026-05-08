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

internal fun MPVActivity.getNightModeLabel(): String = getString(nightModePresetLabelIds[nightModeLevel])

internal fun MPVActivity.getAudioNormLabel(): String = getString(audioNormPresetLabelIds[audioNormLevel])

internal fun MPVActivity.currentVoiceBoostState(): MediaPickerDialog.ValueState {
    val maxLevel = voiceBoostPresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getVoiceBoostLabel(),
        active = isVoiceBoostOn(),
        canDecrease = voiceBoostLevel > 0,
        canIncrease = voiceBoostLevel < maxLevel
    )
}

internal fun MPVActivity.currentVolumeBoostState(): MediaPickerDialog.ValueState {
    val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
    val maxIndex = volumeBoostStepsDb.lastIndex
    return MediaPickerDialog.ValueState(
        label = getVolumeBoostLabel(),
        active = isVolumeBoostOn(),
        canDecrease = currentIndex > 0,
        canIncrease = currentIndex < maxIndex
    )
}

internal fun MPVActivity.currentDownmixState(): MediaPickerDialog.ValueState {
    val maxLevel = downmixPresetLabelIds.lastIndex
    val active = isDownmixOn() && currentAudioChannelCount() >= MIN_SURROUND_CHANNELS
    return MediaPickerDialog.ValueState(
        label = getDownmixLabel(),
        active = active,
        canDecrease = downmixLevel > 0,
        canIncrease = downmixLevel < maxLevel
    )
}

internal fun MPVActivity.currentNightModeState(): MediaPickerDialog.ValueState {
    if (isAudioNormOn()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.filter_blocked_by_audio_norm),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false
        )
    }
    val maxLevel = nightModePresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getNightModeLabel(),
        active = isNightModeOn(),
        enabled = true,
        canDecrease = nightModeLevel > 0,
        canIncrease = nightModeLevel < maxLevel
    )
}

internal fun MPVActivity.currentAudioNormState(): MediaPickerDialog.ValueState {
    if (isNightModeOn()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.filter_blocked_by_drc),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false
        )
    }
    val maxLevel = audioNormPresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getAudioNormLabel(),
        active = isAudioNormOn(),
        enabled = true,
        canDecrease = audioNormLevel > 0,
        canIncrease = audioNormLevel < maxLevel
    )
}

internal fun MPVActivity.currentFilterStates(): MediaPickerDialog.FilterStates {
    return MediaPickerDialog.FilterStates(
        voiceBoost = currentVoiceBoostState(),
        volumeBoost = currentVolumeBoostState(),
        nightMode = currentNightModeState(),
        audioNorm = currentAudioNormState(),
        downmix = currentDownmixState()
    )
}
