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

internal fun MPVActivity.adjustNightMode(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = nightModePresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                nightModeLevel + delta > maxLevel -> 0
                nightModeLevel + delta < 0 -> maxLevel
                else -> nightModeLevel + delta
            }
        }
        else -> (nightModeLevel + delta).coerceIn(0, maxLevel)
    }
    nightModeLevel = nextLevel
    if (nightModeLevel > 0 && audioNormLevel > 0)
        audioNormLevel = 0
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_night_mode),
        if (isNightModeOn()) getNightModeLabel() else getString(R.string.status_off)
    )
    return currentNightModeState()
}

internal fun MPVActivity.adjustAudioNorm(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = audioNormPresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                audioNormLevel + delta > maxLevel -> 0
                audioNormLevel + delta < 0 -> maxLevel
                else -> audioNormLevel + delta
            }
        }
        else -> (audioNormLevel + delta).coerceIn(0, maxLevel)
    }
    audioNormLevel = nextLevel
    if (audioNormLevel > 0 && nightModeLevel > 0)
        nightModeLevel = 0
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_audio_norm),
        if (isAudioNormOn()) getAudioNormLabel() else getString(R.string.status_off)
    )
    return currentAudioNormState()
}

internal fun MPVActivity.isSubScaleOn() = subScaleSteps[subScaleLevel] != DEFAULT_SUB_SCALE

internal fun MPVActivity.isSubPosOn() = subPosSteps[subPosLevel] != DEFAULT_SUB_POSITION_PERCENT

internal fun MPVActivity.isSecondaryPosOn() =
    secondaryPosSteps[secondaryPosLevel] != DEFAULT_SECONDARY_SUB_POSITION_PERCENT

internal fun MPVActivity.getSubScaleLabel(): String =
    if (isSubScaleOn()) String.format(Locale.US, "%.2fx", subScaleSteps[subScaleLevel])
    else getString(R.string.sub_scale_default)

internal fun MPVActivity.getSubPosLabel(): String =
    if (isSubPosOn()) "${subPosSteps[subPosLevel]}%"
    else getString(R.string.sub_pos_default)

internal fun MPVActivity.getSecondaryPosLabel(): String =
    if (isSecondaryPosOn()) "${secondaryPosSteps[secondaryPosLevel]}%"
    else getString(R.string.sub_pos_default)
