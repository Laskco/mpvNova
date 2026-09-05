package app.mpvnova.player

import android.view.WindowManager
import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.appcompat.content.res.AppCompatResources
import app.mpvnova.player.databinding.DialogPlayerTitleStyleBinding

internal fun MPVActivity.openPlayerTitleStylePanel() {
    val restorePlayback = keepPlaybackForDialog()
    val panel = DialogPlayerTitleStyleBinding.inflate(layoutInflater)
    lateinit var dialog: AlertDialog
    val controller = PlayerTitleStylePanelController(
        activity = this,
        panel = panel,
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext),
        onPreviewChanged = { fitPlayerTitleStylePanelBelowPreview(dialog) },
        onMovePanel = { part, refreshControls ->
            startPlayerTitlePanelPlacement(dialog, panel, part) {
                refreshControls()
                fitPlayerTitleStylePanelBelowPreview(dialog)
            }
        },
    )
    controller.bind()

    dialog = AlertDialog.Builder(this).setView(panel.root).create()
    panel.titleStyleDoneBtn.setOnClickListener { dialog.dismiss() }
    dialog.setOnDismissListener {
        playerTextStylePreviewActive = false
        restorePlayback()
        updateMetadataDisplay()
        refreshTimeInfoPanelVisibility()
        refreshVisibleControlsTimeout()
        reopenDrawerIfPending()
    }

    fadeHandler.removeCallbacks(fadeRunnable)
    playerTextStylePreviewActive = true
    UiFont.applyToViewTree(panel.root)
    controller.select(PlayerTitlePart.TITLE)
    showWidePlayerDialog(
        dialog,
        PLAYER_TITLE_STYLE_DIALOG_LAYOUT,
        PlayerDialogChrome.TITLE_AND_CLOCK_PREVIEW,
    )
    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    showPlayerTitleStylePreview()
    fitPlayerTitleStylePanelBelowPreview(dialog)
    panel.titleStyleTitleTab.requestFocus()
}

@Suppress("CyclomaticComplexMethod")
private fun MPVActivity.startPlayerTitlePanelPlacement(
    dialog: AlertDialog,
    panel: DialogPlayerTitleStyleBinding,
    part: PlayerTitlePart,
    onFinished: () -> Unit,
) {
    val target = playerTitlePlacementTarget(part)
    target.post {
        val density = resources.displayMetrics.density
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val current = playerTitleStyle.panelStyleFor(part)
        val initial = current.copy(
            manualPosition = true,
            horizontalOffsetDp = (target.left / density).toInt(),
            verticalOffsetDp = (target.top / density).toInt(),
        )
        playerTitleStyle = playerTitleStyle.withPanelStyle(part, initial)
        applyPlayerTitleStyle()

        val hint = TextView(this).apply {
            background = AppCompatResources.getDrawable(
                this@startPlayerTitlePanelPlacement,
                R.drawable.bg_tv_dialog_shell,
            )
            gravity = android.view.Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextColor(getColor(R.color.tv_text))
            textSize = 13f
        }
        binding.root.addView(
            hint,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                bottomMargin = dp(PLACEMENT_HINT_BOTTOM_MARGIN_DP)
            },
        )
        panel.root.alpha = 0f

        fun renderHint() {
            val position = playerTitleStyle.panelStyleFor(part)
            hint.text = getString(
                R.string.player_title_style_move_hint,
                if (playerTitleStyle.combinedPanels) {
                    getString(R.string.player_title_extra_combined)
                } else if (part.isTitlePart()) {
                    getString(R.string.player_title_style_title_panel)
                } else {
                    getString(R.string.player_title_style_clock_panel)
                },
                position.horizontalOffsetDp,
                position.verticalOffsetDp,
            )
        }

        fun move(horizontalDp: Int, verticalDp: Int) {
            val maxHorizontalDp = ((binding.root.width - target.width).coerceAtLeast(0) / density).toInt()
            val maxVerticalDp = ((binding.root.height - target.height).coerceAtLeast(0) / density).toInt()
            val style = playerTitleStyle.panelStyleFor(part)
            val moved = style.copy(
                manualPosition = true,
                horizontalOffsetDp = (style.horizontalOffsetDp + horizontalDp)
                    .coerceIn(0, maxHorizontalDp),
                verticalOffsetDp = (style.verticalOffsetDp + verticalDp)
                    .coerceIn(0, maxVerticalDp),
            )
            playerTitleStyle = playerTitleStyle.withPanelStyle(part, moved)
            applyPlayerTitleStyle()
            renderHint()
        }

        fun finishPlacement() {
            PlayerTitlePanelStyleStore.write(preferences, playerTitleStyle)
            binding.root.removeView(hint)
            panel.root.alpha = 1f
            dialog.setOnKeyListener { _, _, _ ->
                noteScreensaverActivity()
                false
            }
            onFinished()
            panel.titleStyleMoveBtn.requestFocus()
        }

        renderHint()
        dialog.setOnKeyListener { _, keyCode, event ->
            noteScreensaverActivity()
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK -> finishPlacement()
                else -> return@setOnKeyListener true
            }
            true
        }
    }
}

private fun MPVActivity.dp(value: Int): Int =
    (resources.displayMetrics.density * value).toInt()

private fun MPVActivity.fitPlayerTitleStylePanelBelowPreview(dialog: AlertDialog) {
    binding.playerTitleOverlay.post {
        if (playerTitleStyle.titlePanel.manualPosition) return@post
        val dialogWindow = dialog.window ?: return@post
        val screenHeight = resources.displayMetrics.heightPixels
        val previewGap = Utils.convertDp(activityContext, PLAYER_TITLE_STYLE_PREVIEW_GAP_DP)
        val verticalOffset = Utils.convertDp(
            activityContext,
            PLAYER_TITLE_STYLE_DIALOG_LAYOUT.verticalOffsetDp,
        )
        val availableHeight = (
            screenHeight - playerTitlePlacementTarget(PlayerTitlePart.TITLE).bottom - previewGap - verticalOffset
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
private const val PLACEMENT_HINT_BOTTOM_MARGIN_DP = 24
