package app.mpvnova.player

import androidx.annotation.StringRes

internal fun MPVActivity.adjustPlayerTitleEffects(
    style: PlayerTitleTextStyle,
    control: PlayerTitleStyleControl,
    delta: Int,
): PlayerTitleTextStyle = when (control) {
    PlayerTitleStyleControl.COLOR -> style.copy(
        color = cyclePlayerTitleValue(PlayerTitleColor.entries, style.color, delta),
    )
    PlayerTitleStyleControl.OPACITY -> style.copy(
        opacityPercent = (style.opacityPercent + delta * PLAYER_TITLE_OPACITY_STEP_PERCENT)
            .coerceIn(PLAYER_TITLE_MIN_OPACITY_PERCENT, PLAYER_TITLE_MAX_OPACITY_PERCENT),
    )
    PlayerTitleStyleControl.SHADOW -> style.copy(
        shadow = cyclePlayerTitleValue(PlayerTitleShadow.entries, style.shadow, delta),
    )
    PlayerTitleStyleControl.SHADOW_STRENGTH -> style.copy(
        shadowStrengthPercent = (
            style.shadowStrengthPercent + delta * PLAYER_TITLE_EFFECT_STRENGTH_STEP_PERCENT
        ).coerceIn(
            PLAYER_TITLE_MIN_EFFECT_STRENGTH_PERCENT,
            PLAYER_TITLE_MAX_EFFECT_STRENGTH_PERCENT,
        ),
    )
    PlayerTitleStyleControl.OUTLINE_THICKNESS -> style.copy(
        outlineWidthDp = (
            style.outlineWidthDp + delta * PLAYER_TITLE_OUTLINE_WIDTH_STEP_DP
        ).coerceIn(PLAYER_TITLE_MIN_OUTLINE_WIDTH_DP, PLAYER_TITLE_MAX_OUTLINE_WIDTH_DP),
    )
    PlayerTitleStyleControl.OUTLINE_COLOR -> style.copy(
        outlineColor = cyclePlayerTitleValue(PlayerTitleColor.entries, style.outlineColor, delta),
    )
    PlayerTitleStyleControl.BACKGROUND_PLATE -> style.copy(
        backgroundEnabled = !style.backgroundEnabled,
    )
    PlayerTitleStyleControl.BACKGROUND_STRENGTH -> style.copy(
        backgroundStrengthPercent = (
            style.backgroundStrengthPercent + delta * PLAYER_TITLE_EFFECT_STRENGTH_STEP_PERCENT
        ).coerceIn(
            PLAYER_TITLE_MIN_EFFECT_STRENGTH_PERCENT,
            PLAYER_TITLE_MAX_EFFECT_STRENGTH_PERCENT,
        ),
    )
    PlayerTitleStyleControl.PANEL_OPACITY -> style
    else -> style
}

internal fun MPVActivity.playerTitleEffectControlValue(
    control: PlayerTitleStyleControl,
    style: PlayerTitleTextStyle,
): String = when (control) {
    PlayerTitleStyleControl.COLOR -> playerTitleColorLabel(style.color)
    PlayerTitleStyleControl.OPACITY -> percentLabel(style.opacityPercent)
    PlayerTitleStyleControl.SHADOW -> playerTitleShadowLabel(style.shadow)
    PlayerTitleStyleControl.SHADOW_STRENGTH -> percentLabel(style.shadowStrengthPercent)
    PlayerTitleStyleControl.OUTLINE_THICKNESS -> getString(
        R.string.player_title_style_value_outline_thickness,
        style.outlineWidthDp,
    )
    PlayerTitleStyleControl.OUTLINE_COLOR -> if (style.outlineColor == PlayerTitleColor.APP_COLOR) {
        getString(R.string.player_title_style_value_black)
    } else {
        playerTitleColorLabel(style.outlineColor)
    }
    PlayerTitleStyleControl.BACKGROUND_PLATE -> getString(
        if (style.backgroundEnabled) R.string.player_title_style_value_on
        else R.string.player_title_style_value_off,
    )
    PlayerTitleStyleControl.BACKGROUND_STRENGTH -> percentLabel(
        style.backgroundStrengthPercent,
    )
    PlayerTitleStyleControl.PANEL_OPACITY -> ""
    else -> ""
}

@StringRes
internal fun playerTitleTextCaseLabelRes(textCase: PlayerTitleTextCase): Int = when (textCase) {
    PlayerTitleTextCase.ORIGINAL -> R.string.player_title_style_value_as_provided
    PlayerTitleTextCase.UPPERCASE -> R.string.player_title_style_value_uppercase
    PlayerTitleTextCase.LOWERCASE -> R.string.player_title_style_value_lowercase
    PlayerTitleTextCase.TITLE_CASE -> R.string.player_title_style_value_title_case
}

private fun MPVActivity.percentLabel(value: Int): String = getString(
    R.string.player_title_style_value_opacity,
    value,
)
