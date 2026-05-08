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

internal fun MPVActivity.buildDrcAresampleFilter(): String {
    // Ghidra shows the native player building this exact stage shape:
    //   aresample=<out_rate>:in_chlayout=<in>:out_chlayout=<out>:out_sample_fmt=<fmt>
    // and only then appending :center_mix_level=3.0 when center boost is enabled.
    val outRate = mpvGetPropertyInt("audio-out-params/samplerate")
        ?.takeIf { it > 0 }
        ?: mpvGetPropertyInt("audio-params/samplerate")
            ?.takeIf { it > 0 }
        ?: DEFAULT_AUDIO_SAMPLE_RATE
    val sourceChannels = currentAudioChannelCount()
    val controlledDownmixActive = isDownmixOn() && sourceChannels >= MIN_SURROUND_CHANNELS
    val inputLayout = if (controlledDownmixActive) {
        "stereo"
    } else {
        mpvGetPropertyString("audio-params/channels")
            ?.takeIf { it.isNotBlank() }
    }
    val outputLayout = when {
        controlledDownmixActive -> "stereo"
        else -> mpvGetPropertyString("audio-out-params/channels")
            ?.takeIf { it.isNotBlank() }
            ?: inputLayout
            ?: "stereo"
    }
    val outputFormat = mapMpvAudioFormatToFfmpeg(
        mpvGetPropertyString("audio-out-params/format")
            ?: mpvGetPropertyString("audio-params/format")
    ) ?: "flt"

    val options = mutableListOf("$outRate")
    inputLayout?.let { options += "in_chlayout=$it" }
    options += "out_chlayout=$outputLayout"
    options += "out_sample_fmt=$outputFormat"
    Log.i(
        MPV_ACTIVITY_TAG,
        if (controlledDownmixActive)
            "DRC using controlled Channel Downmix output: ${sourceChannels}ch -> stereo"
        else
            "DRC active without forced center downmix: ${sourceChannels}ch source"
    )
    return "aresample=${options.joinToString(":")}"
}

internal fun MPVActivity.drcVolumeMultiplier(): String {
    // The native player stores integer gain percentages and its transcoder converts
    // them to a linear multiplier via percent/100.
    val percent = (Math.pow(DB_TO_LINEAR_BASE, volumeBoostDb / DB_POWER_DIVISOR) *
        PERCENT_SCALE_DOUBLE).roundToInt()
    val rounded = percent / PERCENT_SCALE_DOUBLE
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.3f", rounded)
            .trimEnd('0')
            .trimEnd('.')
    }
}

internal fun MPVActivity.volumeBoostFilter(): String {
    val dynamicsAlreadyManaged = isAudioNormOn() || isNightModeOn()
    if (dynamicsAlreadyManaged) {
        val limit = when {
            volumeBoostDb <= BOOST_LIMIT_LIGHT_DB -> "0.97"
            volumeBoostDb <= BOOST_LIMIT_MODERATE_DB -> "0.95"
            volumeBoostDb <= BOOST_LIMIT_STRONG_DB -> "0.93"
            volumeBoostDb <= BOOST_LIMIT_HIGH_DB -> "0.91"
            else -> "0.89"
        }
        return "$volumeBoostFilterLabel:lavfi=[" +
            "volume=${volumeBoostDb}dB," +
            "alimiter=limit=$limit:attack=2:release=20]"
    }

    val settings = when {
        volumeBoostDb <= BOOST_LIMIT_LIGHT_DB -> Triple("-19dB", "1.6", "0.95")
        volumeBoostDb <= BOOST_LIMIT_MODERATE_DB -> Triple("-20dB", "1.9", "0.93")
        volumeBoostDb <= BOOST_LIMIT_STRONG_DB -> Triple("-21dB", "2.2", "0.91")
        volumeBoostDb <= BOOST_LIMIT_HIGH_DB -> Triple("-22dB", "2.5", "0.89")
        else -> Triple("-23dB", "2.8", "0.87")
    }
    return "$volumeBoostFilterLabel:lavfi=[" +
        "acompressor=threshold=${settings.first}:ratio=${settings.second}:attack=6:" +
        "release=90:knee=3.0:link=average:detection=rms:makeup=1.02," +
        "volume=${volumeBoostDb}dB," +
        "alimiter=limit=${settings.third}:attack=2:release=20]"
}

internal fun MPVActivity.buildDrcAudioStageFilter(): String {
    val stageFilters = mutableListOf<String>()
    if (isVolumeBoostOn())
        stageFilters += "volume=${drcVolumeMultiplier()}"
    stageFilters += drcFilterBody.trimEnd(',')
    stageFilters += buildDrcAresampleFilter()
    return "$drcAudioStageFilterLabel:lavfi=[${stageFilters.joinToString(",")}]"
}

internal fun MPVActivity.getVoiceBoostLabel(): String = getString(voiceBoostPresetLabelIds[voiceBoostLevel])

internal fun MPVActivity.getDownmixBaseLabel(): String = getString(downmixPresetLabelIds[downmixLevel])

internal fun MPVActivity.getDownmixLabel(): String {
    if (!isDownmixOn()) {
        return getString(R.string.filter_value_off)
    }
    val channels = currentAudioChannelCount()
    return if (channels >= MIN_SURROUND_CHANNELS) {
        getString(R.string.format_downmix_active, getDownmixBaseLabel(), channels)
    } else if (channels <= STEREO_CHANNEL_COUNT) {
        getString(R.string.format_downmix_stereo, getDownmixBaseLabel())
    } else {
        getString(R.string.format_downmix_multichannel, getDownmixBaseLabel(), channels)
    }
}

internal fun MPVActivity.getVolumeBoostLabel(): String {
    return if (volumeBoostDb > 0) {
        getString(R.string.format_db, volumeBoostDb)
    } else {
        getString(R.string.filter_value_off)
    }
}
