package app.mpvnova.player

import android.view.View
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.onBackPressedImpl() {
    val notYetPlayed = psc.playlistCount - psc.playlistPos - 1
    val playlistConfirmsExit = notYetPlayed > 0 && playlistExitWarning
    val decision = decidePlayerBackPress(
        backHidesControlsFirst = backHidesControlsFirst,
        controlsVisible = hasDismissiblePlayerControls(),
        exitWithDoubleBack = exitWithDoubleBack,
        playlistConfirmsExit = playlistConfirmsExit,
        lastBackPressMs = lastBackPressMs,
        nowMs = android.os.SystemClock.uptimeMillis(),
    )
    lastBackPressMs = decision.nextLastBackPressMs
    if (!handlePlayerBackDecision(decision)) {
        continuePlayerExit(notYetPlayed, playlistConfirmsExit)
    }
}

private fun MPVActivity.handlePlayerBackDecision(decision: PlayerBackDecision): Boolean {
    return when (decision.action) {
        PlayerBackAction.HIDE_CONTROLS -> {
            commitPendingSeekbarSeek()
            userIsOperatingSeekbar = false
            hideControls()
            true
        }
        PlayerBackAction.SHOW_EXIT_HINT -> {
            showToast(getString(R.string.exit_double_back_hint))
            true
        }
        PlayerBackAction.CONTINUE_EXIT -> false
    }
}

private fun MPVActivity.continuePlayerExit(notYetPlayed: Int, playlistConfirmsExit: Boolean) {
    if (!playlistConfirmsExit) {
        finishWithResult(RESULT_OK, true)
    } else {
        showPlaylistExitConfirmation(notYetPlayed)
    }
}

private fun MPVActivity.showPlaylistExitConfirmation(notYetPlayed: Int) {
    val restore = pauseForDialog()
    val dialog = with(AlertDialog.Builder(this)) {
        setMessage(getString(R.string.exit_warning_playlist, notYetPlayed))
        setPositiveButton(R.string.dialog_yes) { dialog, _ ->
            dialog.dismiss()
            finishWithResult(RESULT_OK, true)
        }
        setNegativeButton(R.string.dialog_no) { dialog, _ ->
            dialog.dismiss()
            restore()
        }
        create()
    }
    showPlayerDialog(dialog)
}

private fun MPVActivity.hasDismissiblePlayerControls(): Boolean =
    binding.controls.visibility == View.VISIBLE ||
        binding.topControls.visibility == View.VISIBLE ||
        binding.controlsScrim.visibility == View.VISIBLE ||
        binding.playerTitleOverlay.visibility == View.VISIBLE ||
        binding.statsTextView.visibility == View.VISIBLE

internal enum class PlayerBackAction { HIDE_CONTROLS, SHOW_EXIT_HINT, CONTINUE_EXIT }

internal data class PlayerBackDecision(
    val action: PlayerBackAction,
    val nextLastBackPressMs: Long,
)

internal fun decidePlayerBackPress(
    backHidesControlsFirst: Boolean,
    controlsVisible: Boolean,
    exitWithDoubleBack: Boolean,
    playlistConfirmsExit: Boolean,
    lastBackPressMs: Long,
    nowMs: Long,
): PlayerBackDecision {
    val hideControls = backHidesControlsFirst && controlsVisible
    val exitWindowExpired = lastBackPressMs <= 0L ||
        nowMs - lastBackPressMs > DOUBLE_BACK_WINDOW_MS
    val showExitHint = exitWithDoubleBack && !playlistConfirmsExit && exitWindowExpired
    return when {
        hideControls -> PlayerBackDecision(PlayerBackAction.HIDE_CONTROLS, 0L)
        showExitHint -> PlayerBackDecision(PlayerBackAction.SHOW_EXIT_HINT, nowMs)
        else -> PlayerBackDecision(PlayerBackAction.CONTINUE_EXIT, 0L)
    }
}
