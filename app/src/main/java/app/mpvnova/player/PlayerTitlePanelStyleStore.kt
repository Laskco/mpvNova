package app.mpvnova.player

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

internal object PlayerTitlePanelStyleStore {
    private const val PREFIX = "player_title_style"
    private const val TITLE_OPACITY_KEY = "${PREFIX}_title_panel_opacity"
    private const val CLOCK_OPACITY_KEY = "${PREFIX}_clock_panel_opacity"

    fun readTitleOpacity(prefs: SharedPreferences): Int = read(
        prefs,
        TITLE_OPACITY_KEY,
        PlayerTitleStyle.DEFAULT.titlePanelOpacityPercent,
    )

    fun readClockOpacity(prefs: SharedPreferences): Int = read(
        prefs,
        CLOCK_OPACITY_KEY,
        PlayerTitleStyle.DEFAULT.clockPanelOpacityPercent,
    )

    fun write(prefs: SharedPreferences, style: PlayerTitleStyle) {
        prefs.edit()
            .putInt(TITLE_OPACITY_KEY, style.titlePanelOpacityPercent)
            .putInt(CLOCK_OPACITY_KEY, style.clockPanelOpacityPercent)
            .apply()
    }

    fun reset(prefs: SharedPreferences) = write(prefs, PlayerTitleStyle.DEFAULT)

    private fun read(
        prefs: SharedPreferences,
        key: String,
        defaultValue: Int,
    ): Int = prefs.getInt(key, defaultValue).coerceIn(
        PLAYER_TITLE_MIN_PANEL_OPACITY_PERCENT,
        PLAYER_TITLE_MAX_PANEL_OPACITY_PERCENT,
    )
}

internal fun PlayerTitleStyle.panelOpacityFor(part: PlayerTitlePart): Int =
    if (part.isTitlePart()) titlePanelOpacityPercent else clockPanelOpacityPercent

internal fun PlayerTitleStyle.adjustPanelOpacity(
    part: PlayerTitlePart,
    delta: Int,
): PlayerTitleStyle {
    val value = (panelOpacityFor(part) + delta * PLAYER_TITLE_PANEL_OPACITY_STEP_PERCENT)
        .coerceIn(
            PLAYER_TITLE_MIN_PANEL_OPACITY_PERCENT,
            PLAYER_TITLE_MAX_PANEL_OPACITY_PERCENT,
        )
    return if (part.isTitlePart()) {
        copy(titlePanelOpacityPercent = value)
    } else {
        copy(clockPanelOpacityPercent = value)
    }
}

internal fun MPVActivity.applyPlayerTitlePanelGlass() {
    binding.playerTitleOverlay.applyThemedGlassOpacity(
        playerTitleStyle.titlePanelOpacityPercent,
    )
    binding.timeInfoPanel.applyThemedGlassOpacity(
        playerTitleStyle.clockPanelOpacityPercent,
    )
}

private fun View.applyThemedGlassOpacity(opacityPercent: Int) {
    val startColor = context.themedColor(R.attr.mpvSurfaceAltStart, R.color.tv_surface_alt)
    val endColor = context.themedColor(R.attr.mpvSurfaceAltEnd, R.color.tv_surface_alt)
    val strokeColor = context.themedColor(R.attr.mpvStroke, R.color.tv_stroke)
    background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            startColor.withOpacity(opacityPercent),
            endColor.withOpacity(opacityPercent),
        ),
    ).apply {
        cornerRadius = resources.displayMetrics.density * PLAYER_TITLE_GLASS_RADIUS_DP
        setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), strokeColor)
    }
}

private fun Int.withOpacity(percent: Int): Int {
    val alpha = COLOR_CHANNEL_MAX * percent / PLAYER_TITLE_MAX_PANEL_OPACITY_PERCENT
    return Color.argb(
        alpha,
        Color.red(this),
        Color.green(this),
        Color.blue(this),
    )
}

private const val PLAYER_TITLE_GLASS_RADIUS_DP = 22f
private const val COLOR_CHANNEL_MAX = 255
