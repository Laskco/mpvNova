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

internal fun MPVActivity.openAdvancedMenu(restoreState: StateRestoreCallback) {
    genericMenu(
        R.layout.dialog_advanced_menu,
        advancedMenuItems(restoreState),
        advancedHiddenButtons(),
        restoreState
    )
}

internal fun MPVActivity.cycleOrientation() {
    requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

internal fun MPVActivity.openFilePickerFor(title: String, skip: Int?, callback: ActivityResultCallback) {
    val intent = Intent(this, FilePickerActivity::class.java)
    intent.putExtra("title", title)
    intent.putExtra("allow_document", true)
    skip?.let { intent.putExtra("skip", it) }
    // start file picker at directory of current file
    val path = mpvGetPropertyString("path") ?: ""
    if (path.startsWith('/'))
        intent.putExtra("default_path", File(path).parent)

    pendingActivityResultCallback = callback
    filePickerResultLauncher.launch(intent)
}

internal fun MPVActivity.openFilePickerFor(@StringRes titleRes: Int, callback: ActivityResultCallback) {
    openFilePickerFor(getString(titleRes), null, callback)
}

internal fun MPVActivity.refreshUi() {
    // forces update of entire UI, used when resuming the activity
    updatePlaybackStatus(psc.pause)
    updatePlaybackTimeline(psc.position, forceTextUpdate = true)
    updatePlaybackDuration(psc.duration)
    updateAudioUI()
    refreshAllFilterTints()
    updateOrientation()
    updateMetadataDisplay()
    updateDecoderButton()
    updateSpeedButton()
    updatePlaylistButtons()
    player.loadTracks()
    updateChapterMarkers()
}

internal fun MPVActivity.updateAudioUI() {
    // Note: prev/next now live in the button group at all times (TV redesign).
    // For the audio-only UI we just reorder the button group; for video mode
    // we use the full button row including the new audio-filter buttons.
    val audioButtons = arrayOf(R.id.prevBtn, R.id.cycleAudioBtn, R.id.playBtn,
            R.id.nextChapterBtn, R.id.cycleSpeedBtn, R.id.nextBtn)
    val videoButtons = arrayOf(R.id.playBtn, R.id.nextChapterBtn, R.id.prevBtn, R.id.nextBtn,
            R.id.cycleSubsBtn, R.id.cycleAudioBtn,
            R.id.cycleSpeedBtn, R.id.cycleDecoderBtn, R.id.statsToggleBtn,
            R.id.voiceBoostBtn, R.id.volumeBoostBtn, R.id.nightModeBtn, R.id.audioNormBtn)

    val shouldUseAudioUI = isPlayingAudioOnly()
    if (shouldUseAudioUI == useAudioUI)
        return
    useAudioUI = shouldUseAudioUI
    Log.v(MPV_ACTIVITY_TAG, "Audio UI: $useAudioUI")

    val buttonGroup = binding.controlsButtonGroup

    if (useAudioUI) {
        Utils.viewGroupReorder(buttonGroup, audioButtons)

        binding.controlsTitleGroup.visibility = View.VISIBLE
        Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.titleTextView, R.id.minorTitleTextView))
        updateMetadataDisplay()

        showControls()
    } else {
        Utils.viewGroupReorder(buttonGroup, videoButtons)
        binding.controlsTitleGroup.visibility = View.GONE
        updateMetadataDisplay()

        hideControls()
    }

    updatePlaylistButtons()
}

internal fun MPVActivity.updateMetadataDisplay() {
    if (useAudioUI) {
        updatePlayerTitleOverlay()
        binding.titleTextView.setTextIfChanged(psc.meta.formatTitle())
        binding.minorTitleTextView.setTextIfChanged(psc.meta.formatArtistAlbum())
    } else if (showMediaTitle) {
        currentVideoTitle = resolveVlcStyleVideoTitle()
        updatePlayerTitleOverlay()
        binding.fullTitleTextView.setTextIfChanged(currentVideoTitle)
    } else {
        updatePlayerTitleOverlay()
    }
}

internal fun MPVActivity.updatePlayerTitleOverlay() {
    val title = currentVideoTitle?.trim().orEmpty()
    val shouldShow = !useAudioUI &&
        showMediaTitle &&
        title.isNotBlank() &&
        binding.controls.isVisible

    if (!shouldShow) {
        val wasVisible = binding.playerTitleOverlay.visibility == View.VISIBLE
        binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
        if (wasVisible)
            updatePlayerToastPlacement()
        return
    }

    binding.playerTitlePrimary.setTextIfChanged(title)
    binding.playerTitleSecondary.setVisibilityIfChanged(View.GONE)
    updatePlayerTitleWidth()
    if (binding.playerTitleOverlay.alpha != 1f)
        binding.playerTitleOverlay.alpha = 1f
    val wasHidden = binding.playerTitleOverlay.visibility != View.VISIBLE
    binding.playerTitleOverlay.setVisibilityIfChanged(View.VISIBLE)
    if (wasHidden)
        updatePlayerToastPlacement()
}
