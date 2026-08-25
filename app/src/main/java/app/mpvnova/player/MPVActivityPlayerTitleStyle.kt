package app.mpvnova.player

import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogPlayerTitleStyleBinding

internal fun MPVActivity.openPlayerTitleStylePanel() {
    val restorePlayback = keepPlaybackForDialog()
    val restorePlayerBar = hidePlayerBarForTitleEditor()
    val panel = DialogPlayerTitleStyleBinding.inflate(layoutInflater)
    lateinit var dialog: AlertDialog
    val controller = PlayerTitleStylePanelController(
        activity = this,
        panel = panel,
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext),
        onPreviewChanged = { fitPlayerTitleStylePanelBelowPreview(dialog) },
    )
    controller.bind()

    dialog = AlertDialog.Builder(this).setView(panel.root).create()
    panel.titleStyleDoneBtn.setOnClickListener { dialog.dismiss() }
    dialog.setOnDismissListener {
        playerTextStylePreviewActive = false
        restorePlayerBar()
        restorePlayback()
        updateMetadataDisplay()
        refreshTimeInfoPanelVisibility()
        refreshVisibleControlsTimeout()
        reopenDrawerIfPending()
    }

    fadeHandler.removeCallbacks(fadeRunnable)
    playerTextStylePreviewActive = true
    showPlayerTitleStylePreview()
    UiFont.applyToViewTree(panel.root)
    controller.select(PlayerTitlePart.TITLE)
    showWidePlayerDialog(dialog, PLAYER_TITLE_STYLE_DIALOG_LAYOUT)
    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    showPlayerTitleStylePreview()
    fitPlayerTitleStylePanelBelowPreview(dialog)
    panel.titleStyleTitleTab.requestFocus()
}

private fun MPVActivity.hidePlayerBarForTitleEditor(): () -> Unit {
    val controlsWereVisible = binding.controls.visibility
    val scrimWasVisible = binding.controlsScrim.visibility
    binding.controls.animate().setListener(null).cancel()
    binding.controlsScrim.animate().cancel()
    binding.controls.visibility = View.GONE
    binding.controlsScrim.visibility = View.GONE
    return {
        binding.controls.visibility = controlsWereVisible
        binding.controlsScrim.visibility = scrimWasVisible
        binding.controls.alpha = 1f
        binding.controlsScrim.alpha = 1f
    }
}

private fun MPVActivity.fitPlayerTitleStylePanelBelowPreview(dialog: AlertDialog) {
    binding.playerTitleOverlay.post {
        val dialogWindow = dialog.window ?: return@post
        val screenHeight = resources.displayMetrics.heightPixels
        val previewGap = Utils.convertDp(activityContext, PLAYER_TITLE_STYLE_PREVIEW_GAP_DP)
        val verticalOffset = Utils.convertDp(
            activityContext,
            PLAYER_TITLE_STYLE_DIALOG_LAYOUT.verticalOffsetDp,
        )
        val availableHeight = (
            screenHeight - binding.playerTitleOverlay.bottom - previewGap - verticalOffset
        ).coerceAtLeast(1)
        val desiredHeight = minOf(
            (screenHeight * PLAYER_TITLE_STYLE_DIALOG_HEIGHT_FRACTION).toInt(),
            Utils.convertDp(activityContext, PLAYER_TITLE_STYLE_DIALOG_MAX_HEIGHT_DP),
        )
        val currentWidth = dialogWindow.attributes.width.takeIf { it > 0 }
            ?: dialogWindow.decorView.width
        dialogWindow.setLayout(currentWidth, minOf(desiredHeight, availableHeight))
    }
}

internal fun MPVActivity.showPlayerTitleStylePreview() {
    binding.playerTitleSeason.ensurePlayerTextPreview(R.string.player_title_style_sample_season)
    binding.playerTitleEpisodeNumber.ensurePlayerTextPreview(
        R.string.player_title_style_sample_episode_number,
    )
    binding.playerTitlePrimary.ensurePlayerTextPreview(R.string.player_title_style_sample_title)
    binding.playerTitleSecondary.ensurePlayerTextPreview(R.string.player_title_style_sample_episode)
    binding.dateTextView.ensurePlayerTextPreview(R.string.player_title_style_sample_date, true)
    binding.clockTextView.ensurePlayerTextPreview(R.string.player_title_style_sample_clock, true)
    binding.endsAtTextView.ensurePlayerTextPreview(R.string.player_title_style_sample_ends_at, true)
    binding.timeInfoPanel.alpha = 1f
    binding.timeInfoPanel.visibility = View.VISIBLE
    applyPlayerTitleStyle()
    updatePlayerTitleWidth()
    binding.playerTitleOverlay.alpha = 1f
    binding.playerTitleOverlay.visibility = View.VISIBLE
}

private fun TextView.ensurePlayerTextPreview(@StringRes sampleRes: Int, makeVisible: Boolean = false) {
    if (text.isNullOrBlank())
        setText(sampleRes)
    if (makeVisible)
        visibility = View.VISIBLE
}

private const val PLAYER_TITLE_STYLE_PREVIEW_GAP_DP = 18f
