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

internal fun MPVActivity.isDownmixOn() = downmixLevel > 0

internal fun MPVActivity.channelCountFromLayout(layout: String): Int {
    val normalized = layout.trim().lowercase(Locale.US)
    return when {
        normalized == "mono" -> MONO_CHANNEL_COUNT
        normalized == "stereo" || normalized == "2.0" -> STEREO_CHANNEL_COUNT
        normalized.contains("7.1") -> SURROUND_7_1_CHANNEL_COUNT
        normalized.contains("6.1") -> SURROUND_6_1_CHANNEL_COUNT
        normalized.contains("5.1") -> SURROUND_5_1_CHANNEL_COUNT
        normalized.contains("5.0") -> SURROUND_5_0_CHANNEL_COUNT
        normalized.contains("4.0") -> QUAD_CHANNEL_COUNT
        normalized.contains("3.1") -> QUAD_CHANNEL_COUNT
        normalized.contains("3.0") -> FRONT_3_0_CHANNEL_COUNT
        else -> STEREO_CHANNEL_COUNT
    }
}

internal fun MPVActivity.selectedAudioTrackChannelCount(): Int? {
    val trackCount = mpvGetPropertyInt("track-list/count") ?: return null
    return (0 until trackCount)
        .firstOrNull { index ->
            mpvGetPropertyString("track-list/$index/type") == "audio" &&
                mpvGetPropertyBoolean("track-list/$index/selected") == true
        }
        ?.let(::audioTrackChannelCount)
}

internal fun MPVActivity.audioTrackChannelCount(index: Int): Int? {
    mpvGetPropertyInt("track-list/$index/demux-channel-count")?.let { count ->
        if (count > 0) return count
    }
    return mpvGetPropertyString("track-list/$index/demux-channels")?.let(::channelCountFromLayout)
        ?: mpvGetPropertyString("track-list/$index/audio-channels")?.let(::channelCountFromLayout)
}

internal fun MPVActivity.currentAudioChannelCount(): Int {
    return selectedAudioTrackChannelCount()?.takeIf { it > 0 }
        ?: mpvGetPropertyInt("audio-params/channel-count")?.takeIf { it > 0 }
        ?: mpvGetPropertyString("audio-params/channels")?.let(::channelCountFromLayout)
        ?: STEREO_CHANNEL_COUNT
}

internal fun MPVActivity.surroundDialogueDownmixBody(): String? {
    if (currentAudioChannelCount() < MIN_SURROUND_CHANNELS)
        return null
    return when (downmixLevel) {
        DOWNMIX_LIGHT_LEVEL -> "FL=0.88*FL+0.78*FC+0.24*BL+0.24*SL+0.08*BR+0.08*SR+0.18*LFE|" +
            "FR=0.88*FR+0.78*FC+0.24*BR+0.24*SR+0.08*BL+0.08*SL+0.18*LFE"
        DOWNMIX_BALANCED_LEVEL -> "FL=0.84*FL+0.86*FC+0.20*BL+0.20*SL+0.06*BR+0.06*SR+0.16*LFE|" +
            "FR=0.84*FR+0.86*FC+0.20*BR+0.20*SR+0.06*BL+0.06*SL+0.16*LFE"
        DOWNMIX_STRONG_LEVEL -> "FL=0.80*FL+0.94*FC+0.17*BL+0.17*SL+0.05*BR+0.05*SR+0.14*LFE|" +
            "FR=0.80*FR+0.94*FC+0.17*BR+0.17*SR+0.05*BL+0.05*SL+0.14*LFE"
        DOWNMIX_HIGH_LEVEL -> "FL=0.76*FL+1.02*FC+0.14*BL+0.14*SL+0.04*BR+0.04*SR+0.12*LFE|" +
            "FR=0.76*FR+1.02*FC+0.14*BR+0.14*SR+0.04*BL+0.04*SL+0.12*LFE"
        DOWNMIX_MAX_LEVEL -> "FL=0.72*FL+1.10*FC+0.12*BL+0.12*SL+0.03*BR+0.03*SR+0.10*LFE|" +
            "FR=0.72*FR+1.10*FC+0.12*BR+0.12*SR+0.03*BL+0.03*SL+0.10*LFE"
        else -> null
    }
}

internal fun MPVActivity.surroundDialogueDownmixFilter(): String? {
    val body = surroundDialogueDownmixBody() ?: return null
    return "$downmixFilterLabel:lavfi=[pan=stereo|$body]"
}

internal fun MPVActivity.mapMpvAudioFormatToFfmpeg(format: String?): String? {
    return when (format?.trim()?.lowercase(Locale.US)) {
        null, "" -> null
        "u8" -> "u8"
        "s16" -> "s16"
        "s32" -> "s32"
        "s64" -> "s64"
        "float", "flt", "floatle", "floatbe" -> "flt"
        "double", "dbl", "doublele", "doublebe" -> "dbl"
        else -> null
    }
}
