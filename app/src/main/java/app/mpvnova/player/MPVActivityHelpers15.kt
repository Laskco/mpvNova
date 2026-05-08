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

internal fun MPVActivity.refreshAllFilterTints() {
    refreshFilterTint(binding.voiceBoostBtn, isVoiceBoostOn())
    refreshFilterTint(binding.volumeBoostBtn, isVolumeBoostOn())
    refreshFilterTint(binding.nightModeBtn, isNightModeOn())
    refreshFilterTint(binding.audioNormBtn, isAudioNormOn())
}

internal fun MPVActivity.buildAudioFilterChain(): String {
    val filters = mutableListOf<String>()
    if (isNightModeOn()) {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        filters += buildDrcAudioStageFilter()
        if (isVoiceBoostOn())
            filters += drcVoiceBoostPresets[voiceBoostLevel]
    } else {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        if (isAudioNormOn())
            filters += audioNormPresets[audioNormLevel]
        if (isVoiceBoostOn())
            filters += voiceBoostPresets[voiceBoostLevel]
        if (isVolumeBoostOn())
            filters += volumeBoostFilter()
    }
    return filters.joinToString(",")
}

internal fun MPVActivity.applySavedAudioFilterDefaults() {
    val filterChain = if (persistAudioFilters) buildAudioFilterChain() else ""
    mpvSetOptionString("af", filterChain)
}

internal fun MPVActivity.applyAudioFilterState() {
    mpvSetPropertyString("af", buildAudioFilterChain())
}

internal fun MPVActivity.rebuildAudioFilters() {
    applyAudioFilterState()
}

internal fun MPVActivity.adjustVoiceBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = voiceBoostPresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                voiceBoostLevel + delta > maxLevel -> 0
                voiceBoostLevel + delta < 0 -> maxLevel
                else -> voiceBoostLevel + delta
            }
        }
        else -> (voiceBoostLevel + delta).coerceIn(0, maxLevel)
    }
    voiceBoostLevel = nextLevel
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_voice_boost),
        if (isVoiceBoostOn()) getVoiceBoostLabel() else getString(R.string.status_off)
    )
    return currentVoiceBoostState()
}

internal fun MPVActivity.adjustVolumeBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
    val maxIndex = volumeBoostStepsDb.lastIndex
    val nextIndex = when {
        wrap -> {
            when {
                currentIndex + delta > maxIndex -> 0
                currentIndex + delta < 0 -> maxIndex
                else -> currentIndex + delta
            }
        }
        else -> (currentIndex + delta).coerceIn(0, maxIndex)
    }
    volumeBoostDb = volumeBoostStepsDb[nextIndex]
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_volume_boost),
        if (isVolumeBoostOn()) getVolumeBoostLabel() else getString(R.string.status_off)
    )
    return currentVolumeBoostState()
}

internal fun MPVActivity.adjustDownmix(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = downmixPresetLabelIds.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                downmixLevel + delta > maxLevel -> 0
                downmixLevel + delta < 0 -> maxLevel
                else -> downmixLevel + delta
            }
        }
        else -> (downmixLevel + delta).coerceIn(0, maxLevel)
    }
    downmixLevel = nextLevel
    rebuildAudioFilters()
    writeSettings()
    showToast(
        getString(R.string.btn_dialogue_downmix),
        getDownmixLabel()
    )
    return currentDownmixState()
}
