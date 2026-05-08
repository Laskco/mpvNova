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

internal fun TextView.setTextIfChanged(newText: CharSequence?) {
    if (text.toString() != newText.toString())
        text = newText
}

internal fun ImageButton.setImageTintColorIfChanged(color: Int) {
    if (imageTintList?.defaultColor != color)
        imageTintList = ColorStateList.valueOf(color)
}

internal fun MPVActivity.activeFilterColor(): Int {
    cachedActiveFilterColor?.let { return it }
    val color = AppearanceTheme.resolveColor(
        this,
        R.attr.mpvFilterActiveIcon,
        ContextCompat.getColor(this, R.color.tv_filter_active_icon)
    )
    cachedActiveFilterColor = color
    return color
}

internal fun MPVActivity.refreshFilterTint(btn: ImageButton, active: Boolean) {
    val color = if (active)
        activeFilterColor()
    else
        ContextCompat.getColor(this, R.color.tv_text)
    btn.setImageTintColorIfChanged(color)
}

internal fun MPVActivity.isVoiceBoostOn() = voiceBoostLevel > 0

internal fun MPVActivity.isVolumeBoostOn() = volumeBoostDb > 0

internal fun MPVActivity.isNightModeOn() = nightModeLevel > 0

internal fun MPVActivity.isAudioNormOn() = audioNormLevel > 0
