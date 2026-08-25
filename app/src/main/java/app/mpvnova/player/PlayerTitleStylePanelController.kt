package app.mpvnova.player

import android.content.SharedPreferences
import app.mpvnova.player.databinding.DialogPlayerTitleStyleBinding

internal class PlayerTitleStylePanelController(
    private val activity: MPVActivity,
    private val panel: DialogPlayerTitleStyleBinding,
    private val preferences: SharedPreferences,
    private val onPreviewChanged: () -> Unit,
) {
    private var selectedPart = PlayerTitlePart.TITLE
    private val controls = activity.buildPlayerTitleStyleControls(panel, ::adjust)
    private val tabs by lazy {
        mapOf(
            PlayerTitlePart.SEASON to panel.titleStyleSeasonTab,
            PlayerTitlePart.EPISODE_NUMBER to panel.titleStyleEpisodeNumberTab,
            PlayerTitlePart.TITLE to panel.titleStyleTitleTab,
            PlayerTitlePart.EPISODE_TITLE to panel.titleStyleEpisodeTab,
            PlayerTitlePart.DATE to panel.titleStyleDateTab,
            PlayerTitlePart.CLOCK to panel.titleStyleClockTab,
            PlayerTitlePart.ENDS_AT to panel.titleStyleEndsAtTab,
        )
    }

    fun bind() {
        panel.root.setTag(R.id.player_title_style_controls, controls)
        tabs.forEach { (part, button) -> button.setOnClickListener { select(part) } }
        panel.titleStyleResetPartBtn.setOnClickListener { resetSelected() }
        panel.titleStyleResetAllBtn.setOnClickListener { resetAll() }
        panel.titleStyleVisibilityBtn.setOnClickListener { toggleVisibility() }
        panel.titleStyleSeparatorBtn.setOnClickListener { cycleSeparator() }
    }

    fun select(part: PlayerTitlePart) {
        selectedPart = part
        tabs.forEach { (candidate, button) ->
            button.isActivated = candidate == part
            button.isSelected = candidate == part
        }
        panel.titleStyleResetPartBtn.text = activity.getString(
            R.string.player_title_style_reset_part_named,
            activity.playerTitlePartLabel(part),
        )
        refreshControls()
    }

    private fun adjust(control: PlayerTitleStyleControl, delta: Int) {
        if (control == PlayerTitleStyleControl.POSITION) {
            activity.playerTitleStyle = activity.playerTitleStyle.movePart(selectedPart, delta)
            PlayerTitleStyleStore.writeOrders(preferences, activity.playerTitleStyle)
            refreshPreview()
            return
        }
        val updated = activity.adjustPlayerTitleStyle(
            activity.playerTitleStyle.forPart(selectedPart),
            control,
            delta,
        )
        activity.playerTitleStyle = activity.playerTitleStyle.withPart(selectedPart, updated)
        PlayerTitleStyleStore.writePart(preferences, selectedPart, updated)
        refreshPreview()
    }

    private fun resetSelected() {
        PlayerTitleStyleStore.resetPart(preferences, selectedPart)
        reloadStyle()
    }

    private fun resetAll() {
        PlayerTitleStyleStore.resetAll(preferences)
        reloadStyle()
    }

    private fun toggleVisibility() {
        val updated = activity.playerTitleStyle.forPart(selectedPart).let { style ->
            style.copy(visible = !style.visible)
        }
        activity.playerTitleStyle = activity.playerTitleStyle.withPart(selectedPart, updated)
        PlayerTitleStyleStore.writePart(preferences, selectedPart, updated)
        refreshPreview()
    }

    private fun cycleSeparator() {
        val separator = cyclePlayerTitleValue(
            PlayerTitleSeparator.entries,
            activity.playerTitleStyle.separator,
            1,
        )
        activity.playerTitleStyle = activity.playerTitleStyle.copy(separator = separator)
        PlayerTitleStyleStore.writeSeparator(preferences, separator)
        refreshPreview()
    }

    private fun reloadStyle() {
        activity.playerTitleStyle = PlayerTitleStyleStore.read(preferences)
        refreshPreview()
    }

    private fun refreshPreview() {
        activity.applyPlayerTitleStyle()
        activity.showPlayerTitleStylePreview()
        refreshControls()
        onPreviewChanged()
    }

    private fun refreshControls() {
        activity.renderPlayerTitleStyleControls(controls, selectedPart)
        activity.renderPlayerTitleFooterActions(panel, selectedPart)
    }
}
