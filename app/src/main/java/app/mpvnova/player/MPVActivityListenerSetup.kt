package app.mpvnova.player

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

internal fun MPVActivity.bindClickListeners() = with(binding) {
    prevBtn.setOnClickListener { playlistPrev() }
    nextBtn.setOnClickListener { playlistNext() }
    cycleAudioBtn.setOnClickListener { pickAudio() }
    cycleSubsBtn.setOnClickListener { pickSub() }
    playBtn.setOnClickListener { player.cyclePause() }
    cycleDecoderBtn.setOnClickListener { pickDecoder() }
    statsToggleBtn.setOnClickListener { toggleStatsOverlay() }
    cycleSpeedBtn.setOnClickListener { cycleSpeed() }
    voiceBoostBtn.setOnClickListener { adjustVoiceBoost(1, wrap = true) }
    volumeBoostBtn.setOnClickListener { adjustVolumeBoost(1, wrap = true) }
    nightModeBtn.setOnClickListener { adjustNightMode(1, wrap = true) }
    audioNormBtn.setOnClickListener { adjustAudioNorm(1, wrap = true) }
    nextChapterBtn.setOnClickListener { seekChapterRelative(1) }
    topLockBtn.setOnClickListener { lockUI() }
    topPiPBtn.setOnClickListener { goIntoPiP() }
    topMenuBtn.setOnClickListener { openTopMenu() }
    unlockBtn.setOnClickListener { unlockUI() }
    playbackDurationTxt.setOnClickListener { toggleTimeRemainingDisplay() }
}

internal fun MPVActivity.bindLongClickListeners() = with(binding) {
    cycleAudioBtn.setOnLongClickListener { cycleAudio(); true }
    cycleSpeedBtn.setOnLongClickListener { pickSpeed(); true }
    cycleSubsBtn.setOnLongClickListener { cycleSub(); true }
    prevBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
    nextBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
    cycleDecoderBtn.setOnLongClickListener { cycleDecoderMode(); true }
    statsToggleBtn.setOnLongClickListener { showFirstStatsPage(); true }
    voiceBoostBtn.setOnLongClickListener { adjustVoiceBoost(-1, wrap = true); true }
    volumeBoostBtn.setOnLongClickListener { adjustVolumeBoost(-1, wrap = true); true }
    nightModeBtn.setOnLongClickListener { adjustNightMode(-1, wrap = true); true }
    audioNormBtn.setOnLongClickListener { adjustAudioNorm(-1, wrap = true); true }
    nextChapterBtn.setOnLongClickListener { showChapterPickerDialog(); true }
}

internal fun MPVActivity.bindSeekbarListeners() = with(binding.playbackSeekbar) {
    setOnSeekBarChangeListener(seekBarChangeListener)
    keyProgressIncrement = seekbarProgressFromMillis(SEEK_BAR_DPAD_STEP_MS)
}

internal fun MPVActivity.bindTouchAndInsetsListeners() {
    player.setOnTouchListener { _, event ->
        if (lockedUI) {
            false
        } else {
            val handled = gestures.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                player.performClick()
            }
            handled
        }
    }

    ViewCompat.setOnApplyWindowInsetsListener(binding.outside) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updateLayoutParams<MarginLayoutParams> {
            leftMargin = insets.left
            topMargin = insets.top
            bottomMargin = insets.bottom
            rightMargin = insets.right
        }
        WindowInsetsCompat.CONSUMED
    }
}

internal fun MPVActivity.bindActivityCallbacks() {
    onBackPressedDispatcher.addCallback(this) { onBackPressedImpl() }
    addOnPictureInPictureModeChangedListener { info ->
        onPiPModeChangedImpl(info.isInPictureInPictureMode)
    }
}

internal fun MPVActivity.toggleTimeRemainingDisplay() {
    useTimeRemaining = !useTimeRemaining
    updatePlaybackText(psc.positionSec, force = true)
    updatePlaybackDuration(psc.duration)
}

internal fun MPVActivity.showFirstStatsPage() {
    mpvCommand(arrayOf("script-binding", "stats/display-page-1"))
}
