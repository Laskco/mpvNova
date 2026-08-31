package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
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
    PANEL_SURFACE(R.string.player_ui_surface_style),
    PANEL_OPACITY(R.string.player_title_style_panel_opacity),
    PANEL_ACCENT_STRENGTH(R.string.player_title_style_panel_accent_strength),
    PANEL_GRADIENT(R.string.player_ui_gradient),
    PANEL_OUTLINE(R.string.player_ui_panel_outline),
    PANEL_OUTLINE_WIDTH(R.string.player_ui_panel_outline_width),
    PANEL_CORNER_RADIUS(R.string.player_ui_corner_radius),
    PANEL_ELEVATION(R.string.player_ui_panel_elevation),
    PANEL_HORIZONTAL_PADDING(R.string.player_ui_horizontal_padding),
    PANEL_VERTICAL_PADDING(R.string.player_title_style_panel_vertical_padding),
    PANEL_CONTENT_SPACING(R.string.player_title_style_panel_content_spacing),
    PANEL_ALIGNMENT(R.string.player_title_style_panel_alignment),
    PANEL_CONTENT_ALIGNMENT(R.string.player_title_style_panel_content_alignment),
    PANEL_WIDTH(R.string.player_title_style_panel_width),
    PANEL_VERTICAL_OFFSET(R.string.player_title_style_panel_vertical_offset),
}

internal data class PlayerTitleStyleControlView(
    val control: PlayerTitleStyleControl,
    val binding: DialogPlayerTitleStyleControlBinding,
)

internal fun MPVActivity.buildPlayerTitleStyleControls(
    panel: DialogPlayerTitleStyleBinding,
    onAdjust: (PlayerTitleStyleControl, Int) -> Unit,
): List<PlayerTitleStyleControlView> {
    panel.titleStyleControlsContainer.removeAllViews()
    return buildList {
        playerTitleControlSections().forEachIndexed { sectionIndex, section ->
            panel.titleStyleControlsContainer.addView(
                playerTitleSectionHeader(section.labelRes, sectionIndex > 0),
            )
            section.controls.chunked(CONTROLS_PER_ROW).forEachIndexed { rowIndex, chunk ->
                val row = LinearLayout(this@buildPlayerTitleStyleControls).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (rowIndex > 0) {
                            topMargin = Utils.convertDp(context, CONTROL_ROW_GAP_DP)
                        }
                    }
                    isBaselineAligned = false
                    orientation = LinearLayout.HORIZONTAL
                }
                panel.titleStyleControlsContainer.addView(row)
                chunk.forEach { control ->
                    val binding = DialogPlayerTitleStyleControlBinding.inflate(
                        LayoutInflater.from(this@buildPlayerTitleStyleControls),
                        row,
                        false,
                    )
                    binding.titleStyleControlLabel.setText(control.labelRes)
                    binding.titleStyleControlPrevious.setOnClickListener { onAdjust(control, -1) }
                    binding.titleStyleControlNext.setOnClickListener { onAdjust(control, 1) }
                    row.addView(binding.root)
                    add(PlayerTitleStyleControlView(control, binding))
                }
                balanceFinalControlRow(row)
            }
        }
    }
}

private data class PlayerTitleControlSection(
    @StringRes val labelRes: Int,
    val controls: List<PlayerTitleStyleControl>,
)

private fun playerTitleControlSections() = listOf(
    PlayerTitleControlSection(
        R.string.player_title_style_section_text,
        listOf(
            PlayerTitleStyleControl.FONT,
            PlayerTitleStyleControl.SIZE,
            PlayerTitleStyleControl.WEIGHT,
            PlayerTitleStyleControl.LETTER_SPACING,
            PlayerTitleStyleControl.COLOR,
            PlayerTitleStyleControl.OPACITY,
            PlayerTitleStyleControl.ITALIC,
            PlayerTitleStyleControl.TEXT_CASE,
            PlayerTitleStyleControl.POSITION,
        ),
    ),
    PlayerTitleControlSection(
        R.string.player_title_style_section_readability,
        listOf(
            PlayerTitleStyleControl.SHADOW,
            PlayerTitleStyleControl.SHADOW_STRENGTH,
            PlayerTitleStyleControl.OUTLINE_THICKNESS,
            PlayerTitleStyleControl.OUTLINE_COLOR,
            PlayerTitleStyleControl.BACKGROUND_PLATE,
            PlayerTitleStyleControl.BACKGROUND_STRENGTH,
        ),
    ),
    PlayerTitleControlSection(
        R.string.player_title_style_section_panel_appearance,
        listOf(
            PlayerTitleStyleControl.PANEL_SURFACE,
            PlayerTitleStyleControl.PANEL_OPACITY,
            PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH,
            PlayerTitleStyleControl.PANEL_GRADIENT,
            PlayerTitleStyleControl.PANEL_OUTLINE,
            PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH,
            PlayerTitleStyleControl.PANEL_CORNER_RADIUS,
            PlayerTitleStyleControl.PANEL_ELEVATION,
        ),
    ),
    PlayerTitleControlSection(
        R.string.player_title_style_section_panel_layout,
        listOf(
            PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING,
            PlayerTitleStyleControl.PANEL_VERTICAL_PADDING,
            PlayerTitleStyleControl.PANEL_CONTENT_SPACING,
            PlayerTitleStyleControl.PANEL_ALIGNMENT,
            PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT,
            PlayerTitleStyleControl.PANEL_WIDTH,
            PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET,
        ),
    ),
)

private fun MPVActivity.playerTitleSectionHeader(
    @StringRes labelRes: Int,
    hasTopGap: Boolean,
) = TextView(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = Utils.convertDp(context, if (hasTopGap) SECTION_TOP_GAP_DP else 0f)
        bottomMargin = Utils.convertDp(context, SECTION_BOTTOM_GAP_DP)
    }
    setText(labelRes)
    setTextColor(themedColor(R.attr.mpvAccentHot, R.color.tv_purple_hot))
    setTypeface(typeface, Typeface.BOLD)
    textSize = SECTION_TEXT_SIZE_SP
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
    PlayerTitleStyleControl.PANEL_SURFACE,
    PlayerTitleStyleControl.PANEL_OPACITY,
    PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH,
    PlayerTitleStyleControl.PANEL_GRADIENT,
    PlayerTitleStyleControl.PANEL_OUTLINE,
    PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH,
    PlayerTitleStyleControl.PANEL_CORNER_RADIUS,
    PlayerTitleStyleControl.PANEL_ELEVATION,
    PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING,
    PlayerTitleStyleControl.PANEL_VERTICAL_PADDING,
    PlayerTitleStyleControl.PANEL_CONTENT_SPACING,
    PlayerTitleStyleControl.PANEL_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_WIDTH,
    PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET,
    -> style
}

internal fun PlayerTitleStyleControl.isPanelControl(): Boolean = when (this) {
    PlayerTitleStyleControl.PANEL_SURFACE,
    PlayerTitleStyleControl.PANEL_OPACITY,
    PlayerTitleStyleControl.PANEL_ACCENT_STRENGTH,
    PlayerTitleStyleControl.PANEL_GRADIENT,
    PlayerTitleStyleControl.PANEL_OUTLINE,
    PlayerTitleStyleControl.PANEL_OUTLINE_WIDTH,
    PlayerTitleStyleControl.PANEL_CORNER_RADIUS,
    PlayerTitleStyleControl.PANEL_ELEVATION,
    PlayerTitleStyleControl.PANEL_HORIZONTAL_PADDING,
    PlayerTitleStyleControl.PANEL_VERTICAL_PADDING,
    PlayerTitleStyleControl.PANEL_CONTENT_SPACING,
    PlayerTitleStyleControl.PANEL_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_CONTENT_ALIGNMENT,
    PlayerTitleStyleControl.PANEL_WIDTH,
    PlayerTitleStyleControl.PANEL_VERTICAL_OFFSET,
    -> true
    else -> false
}

private fun balanceFinalControlRow(row: LinearLayout) {
    repeat(CONTROLS_PER_ROW - row.childCount) {
        row.addView(View(row.context), LinearLayout.LayoutParams(0, 1, 1f))
    }
}

private const val CONTROLS_PER_ROW = 4
private const val CONTROL_ROW_GAP_DP = 8f
private const val SECTION_TOP_GAP_DP = 12f
private const val SECTION_BOTTOM_GAP_DP = 6f
private const val SECTION_TEXT_SIZE_SP = 11f
