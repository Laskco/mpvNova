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

internal fun MPVActivity.updatePlaylistButtons() {
    val plCount = psc.playlistCount
    val plPos = psc.playlistPos

    if (!useAudioUI && plCount == 1) {
        // use View.GONE so the buttons won't take up any space
        binding.prevBtn.setVisibilityIfChanged(View.GONE)
        binding.nextBtn.setVisibilityIfChanged(View.GONE)
        return
    }
    binding.prevBtn.setVisibilityIfChanged(View.VISIBLE)
    binding.nextBtn.setVisibilityIfChanged(View.VISIBLE)

    val g = ContextCompat.getColor(this, R.color.tint_disabled)
    val w = ContextCompat.getColor(this, R.color.tint_normal)
    binding.prevBtn.setImageTintColorIfChanged(if (plPos == 0) g else w)
    binding.nextBtn.setImageTintColorIfChanged(if (plPos == plCount-1) g else w)
}

internal fun MPVActivity.seekChapterRelative(direction: Int) {
    val chapters = cachedChapters.ifEmpty {
        player.loadChapters().also { cachedChapters = it }
    }
    if (chapters.isEmpty()) {
        mpvCommand(arrayOf("add", "chapter", direction.toString()))
        return
    }

    val referenceTime = pendingChapterSeekTime
        ?: mpvGetPropertyDouble("time-pos/full")
        ?: (psc.position / MPV_MILLIS_PER_SECOND_DOUBLE)

    val target = if (direction > 0) {
        chapters.firstOrNull { it.time > referenceTime + CHAPTER_SKIP_EPSILON_SEC }
    } else {
        chapters.lastOrNull { it.time < referenceTime - CHAPTER_SKIP_EPSILON_SEC }
    }

    if (target == null) {
        pendingChapterSeekTime = null
        mpvCommand(arrayOf("add", "chapter", direction.toString()))
        return
    }

    pendingChapterSeekTime = target.time
    eventUiHandler.removeCallbacks(clearPendingChapterSeek)
    eventUiHandler.postDelayed(clearPendingChapterSeek, CHAPTER_SEEK_MEMORY_MS)

    mpvCommand(arrayOf("seek", target.time.toString(), "absolute+exact"))
    val targetMs = (target.time * MPV_MILLIS_PER_SECOND_DOUBLE).roundToLong().coerceAtLeast(0L)
    setPlaybackSeekbarProgress(seekbarProgressFromMillis(targetMs))
    updatePlaybackTimeline(targetMs, forceTextUpdate = true)
}

internal fun MPVActivity.updateChapterMarkers() {
    val duration = psc.durationSec
    val chapters = player.loadChapters()
    cachedChapters = chapters
    val hasChapters = chapters.isNotEmpty()

    binding.nextChapterBtn.visibility = if (hasChapters) View.VISIBLE else View.GONE

    if (!hasChapters || duration <= 0) {
        binding.playbackSeekbar.clearChapters()
        return
    }

    binding.playbackSeekbar.setChapters(chapters.map { it.time }, duration.toDouble())
}

internal fun MPVActivity.showChapterPickerDialog() {
    val chapters = player.loadChapters()
    if (chapters.isEmpty()) return
    val restore = keepPlaybackForDialog()
    val items = chapters.map { ch ->
        val timecode = Utils.prettyTime(ch.time.roundToInt())
        val title = ch.title?.takeIf { it.isNotBlank() }
            ?: "${getString(R.string.chapter_button)} ${ch.index + 1}"
        ChapterPickerDialog.Item(ch.index, title, timecode)
    }
    val selected = mpvGetPropertyInt("chapter") ?: 0
    val impl = ChapterPickerDialog(items, selected)
    lateinit var dialog: AlertDialog
    impl.onItemPicked = { item ->
        mpvSetPropertyInt("chapter", item.index)
        dialog.dismiss()
    }
    impl.onCancelClick = { dialog.cancel() }
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater))
        setOnDismissListener { restore() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.46f,
            maxWidthDp = 560f,
            heightFraction = 0.62f,
            maxHeightDp = 540f,
        )
    )
}

internal fun MPVActivity.updateOrientation(initial: Boolean = false) {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
        return
    if (autoRotationMode != "auto" && initial) {
        requestedOrientation = fixedOrientationForMode(autoRotationMode)
    } else if (autoRotationMode == "auto" && !initial && player.vid != -1) {
        val ratio = player.getVideoAspect()?.toFloat() ?: 0f
        requestedOrientation = autoOrientationForAspect(ratio)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.makeRemoteAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    intentAction: String
): RemoteAction {
    val intent = NotificationButtonReceiver.createIntent(this, intentAction)
    return RemoteAction(
        Icon.createWithResource(this, icon),
        getString(title),
        REMOTE_ACTION_EMPTY_TEXT,
        intent
    )
}

internal fun MPVActivity.updatePiPParams(force: Boolean = false) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return
    if (!isInPictureInPictureMode && !force)
        return

    try {
        setPictureInPictureParams(buildPiPParams())
    } catch (ignored: IllegalArgumentException) {
        // Android has some limits of what the aspect ratio can be
        setPictureInPictureParams(buildPiPParams(Rational(SQUARE_ASPECT_RATIO, SQUARE_ASPECT_RATIO)))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.buildPiPParams(fallbackAspectRatio: Rational? = null): PictureInPictureParams {
    val playPauseAction = if (psc.pause)
        makeRemoteAction(R.drawable.ic_play_arrow_black_24dp, R.string.btn_play, "PLAY_PAUSE")
    else
        makeRemoteAction(R.drawable.ic_pause_black_24dp, R.string.btn_pause, "PLAY_PAUSE")
    val actions = mutableListOf<RemoteAction>()
    if (psc.playlistCount > 1) {
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_previous_black_24dp, R.string.dialog_prev, "ACTION_PREV"
        ))
        actions.add(playPauseAction)
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_next_black_24dp, R.string.dialog_next, "ACTION_NEXT"
        ))
    } else {
        actions.add(playPauseAction)
    }

    return with(PictureInPictureParams.Builder()) {
        val aspect = fallbackAspectRatio ?: Rational(
            (player.getVideoAspect() ?: 0.0).times(PIP_ASPECT_RATIO_SCALE).toInt(),
            PIP_ASPECT_RATIO_SCALE
        )
        setAspectRatio(aspect)
        setActions(actions)
        build()
    }
}
