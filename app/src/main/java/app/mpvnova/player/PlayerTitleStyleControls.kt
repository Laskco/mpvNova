package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import app.mpvnova.player.databinding.DialogPlayerTitleStyleBinding
import app.mpvnova.player.databinding.DialogPlayerTitleStyleControlBinding

internal enum class PlayerTitleStyleControl(@StringRes val labelRes: Int) {
    FONT(R.string.player_title_style_font),
    SIZE(R.string.player_title_style_size),
    WEIGHT(R.string.player_title_style_weight),
    LETTER_SPACING(R.string.player_title_style_spacing),
    COLOR(R.string.player_title_style_color),
    OPACITY(R.string.player_title_style_opacity),
    SHADOW(R.string.player_title_style_shadow),
    SHADOW_STRENGTH(R.string.player_title_style_shadow_strength),
    OUTLINE_THICKNESS(R.string.player_title_style_outline_thickness),
    OUTLINE_COLOR(R.string.player_title_style_outline_color),
    BACKGROUND_PLATE(R.string.player_title_style_background_plate),
    BACKGROUND_STRENGTH(R.string.player_title_style_background_strength),
    ITALIC(R.string.player_title_style_italic),
    TEXT_CASE(R.string.player_title_style_text_case),
    POSITION(R.string.player_title_style_position),
    PANEL_OPACITY(R.string.player_title_style_panel_opacity),
}

internal data class PlayerTitleStyleControlView(
    val control: PlayerTitleStyleControl,
    val binding: DialogPlayerTitleStyleControlBinding,
)

internal fun MPVActivity.buildPlayerTitleStyleControls(
    panel: DialogPlayerTitleStyleBinding,
    onAdjust: (PlayerTitleStyleControl, Int) -> Unit,
): List<PlayerTitleStyleControlView> {
    val rows = listOf(
        panel.titleStyleControlsRowTop,
        panel.titleStyleControlsRowMiddle,
        panel.titleStyleControlsRowBottom,
        panel.titleStyleControlsRowFourth,
    )
    return PlayerTitleStyleControl.entries.mapIndexed { index, control ->
        val binding = DialogPlayerTitleStyleControlBinding.inflate(
            LayoutInflater.from(this),
            rows[index / CONTROLS_PER_ROW],
            false,
        )
        binding.titleStyleControlLabel.setText(control.labelRes)
        binding.titleStyleControlPrevious.setOnClickListener { onAdjust(control, -1) }
        binding.titleStyleControlNext.setOnClickListener { onAdjust(control, 1) }
        rows[index / CONTROLS_PER_ROW].addView(binding.root)
        PlayerTitleStyleControlView(control, binding)
    }.also { balanceFinalControlRow(rows.last()) }
}

internal fun MPVActivity.renderPlayerTitleStyleControls(
    controls: List<PlayerTitleStyleControlView>,
    part: PlayerTitlePart,
) {
    val style = playerTitleStyle.forPart(part)
    controls.forEach { controlView ->
        controlView.binding.titleStyleControlValue.text = playerTitleControlValue(
            controlView.control,
            style,
            part,
        )
    }
}

internal fun MPVActivity.renderPlayerTitleFooterActions(
    panel: DialogPlayerTitleStyleBinding,
    part: PlayerTitlePart,
) {
    val style = playerTitleStyle.forPart(part)
    panel.titleStyleVisibilityBtn.text = getString(
        R.string.player_title_style_visibility_value,
        playerTitlePartLabel(part),
        getString(
            if (style.visible) {
                R.string.player_title_style_value_shown
            } else {
                R.string.player_title_style_value_hidden
            },
        ),
    )
    panel.titleStyleVisibilityBtn.isActivated = !style.visible

    val canChooseSeparator = part == PlayerTitlePart.SEASON ||
        part == PlayerTitlePart.EPISODE_NUMBER
    panel.titleStyleSeparatorBtn.visibility = if (canChooseSeparator) View.VISIBLE else View.GONE
    panel.titleStyleSeparatorBtn.text = getString(
        R.string.player_title_style_separator_value,
        playerTitleSeparatorLabel(playerTitleStyle.separator),
    )
}

internal fun MPVActivity.adjustPlayerTitleStyle(
    style: PlayerTitleTextStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitleTextStyle = when (control) {
    PlayerTitleStyleControl.FONT -> style.copy(
        font = cyclePlayerTitleValue(playerTitleFontValues(), style.font, delta),
    )
    PlayerTitleStyleControl.SIZE -> style.copy(
        sizeSp = (style.sizeSp + delta * PLAYER_TITLE_SIZE_STEP_SP)
            .coerceIn(PLAYER_TITLE_MIN_CUSTOM_SIZE_SP, PLAYER_TITLE_MAX_CUSTOM_SIZE_SP),
    )
    PlayerTitleStyleControl.WEIGHT -> style.copy(
        weight = cyclePlayerTitleValue(PlayerTitleWeight.entries, style.weight, delta),
    )
    PlayerTitleStyleControl.LETTER_SPACING -> style.copy(
        letterSpacing = (style.letterSpacing + delta * PLAYER_TITLE_LETTER_SPACING_STEP)
            .coerceIn(PLAYER_TITLE_MIN_LETTER_SPACING, PLAYER_TITLE_MAX_LETTER_SPACING),
    )
    PlayerTitleStyleControl.COLOR,
    PlayerTitleStyleControl.OPACITY,
    PlayerTitleStyleControl.SHADOW,
    PlayerTitleStyleControl.SHADOW_STRENGTH,
    PlayerTitleStyleControl.OUTLINE_THICKNESS,
    PlayerTitleStyleControl.OUTLINE_COLOR,
    PlayerTitleStyleControl.BACKGROUND_PLATE,
    PlayerTitleStyleControl.BACKGROUND_STRENGTH,
    -> adjustPlayerTitleEffects(style, control, delta)
    PlayerTitleStyleControl.ITALIC -> style.copy(italic = !style.italic)
    PlayerTitleStyleControl.TEXT_CASE -> style.copy(
        textCase = cyclePlayerTitleValue(PlayerTitleTextCase.entries, style.textCase, delta),
    )
    PlayerTitleStyleControl.POSITION -> style
    PlayerTitleStyleControl.PANEL_OPACITY -> style
}

private fun balanceFinalControlRow(row: LinearLayout) {
    repeat(CONTROLS_PER_ROW - row.childCount) {
        row.addView(View(row.context), LinearLayout.LayoutParams(0, 1, 1f))
    }
}

private const val CONTROLS_PER_ROW = 4
