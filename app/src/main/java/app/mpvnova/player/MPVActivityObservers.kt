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

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class MpvActivityLifecycleObserver(private val activity: MPVActivity) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        activity.activityIsStopped = false
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.activityIsStopped = true
    }
}

internal class MpvActivityEventObserver(private val activity: MPVActivity) : MpvEventObserver {
    override fun eventProperty(property: String) {
        with(activity) {
        val metaUpdated = psc.update(property)
        if (metaUpdated)
            updateMediaSession()
        if (property == "loop-file" || property == "loop-playlist") {
            mediaSession?.setRepeatMode(when (player.getRepeat()) {
                2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            })
        } else if (property == "current-tracks/audio/selected") {
            updateAudioPresence()
            if (persistAudioFilters) {
                rebuildAudioFilters()
                eventUiHandler.post { refreshAllFilterTints() }
            }
        }

        if (property == "pause" || property == "current-tracks/audio/selected")
            handleAudioFocus()

        if (!activityIsForeground) return
        eventUiHandler.post { eventMetadataPropertyUi(property, metaUpdated) }
    }
    }


    override fun eventProperty(property: String, value: Boolean) {
        with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated)
            updateMediaSession()
        if (property == "shuffle") {
            mediaSession?.setShuffleMode(if (value)
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            else
                PlaybackStateCompat.SHUFFLE_MODE_NONE)
        } else if (property == "mute") {
            updateAudioPresence()
        }

        if (metaUpdated || property == "mute")
            handleAudioFocus()

        if (!activityIsForeground) return
        eventUiHandler.post { eventBooleanPropertyUi(property, value) }
    }
    }


    override fun eventProperty(property: String, value: Long) {
        with(activity) {
        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventLongPropertyUi(property) }
    }
    }


    override fun eventProperty(property: String, value: Double) {
        with(activity) {
        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventDoublePropertyUi(property) }
    }
    }


    override fun eventProperty(property: String, value: String) {
        with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated)
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventStringPropertyUi(property, metaUpdated) }
    }
    }


    override fun event(eventId: Int) {
        with(activity) {
        handleMpvEvent(eventId)
    }
    }

}

internal class MpvActivityLogObserver(private val activity: MPVActivity) : MpvLogObserver {
    override fun logMessage(prefix: String, level: Int, text: String) {
        with(activity) {
        updateGpuNextRetryFrameConfirmation(prefix, text)
        maybeApplyGpuNextRenderFallback(prefix, level, text)

        val shouldShowHint = !audioNormUnderrunHintShown &&
            activityIsForeground &&
            text.contains("Audio device underrun detected", ignoreCase = true) &&
            isAudioNormOn() &&
            !isDownmixOn() &&
            currentAudioChannelCount() >= MIN_SURROUND_CHANNELS

        if (shouldShowHint) {
            audioNormUnderrunHintShown = true
            eventUiHandler.post {
                showToast(
                    getString(R.string.btn_audio_norm),
                    getString(R.string.toast_audio_norm_surround_hint)
                )
            }
        }
    }
    }

}

internal class MpvActivityTouchGestureObserver(private val activity: MPVActivity) : TouchGesturesObserver {
    override fun onPropertyChange(p: PropertyChange, diff: Float) {
        with(activity) {
        handleGestureChange(p, diff)
    }
    }

}
