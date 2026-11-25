package app.mpvnova.player

import android.content.SharedPreferences
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

internal object PlayerTitlePanelStyleStore {
    private const val PREFIX = "player_title_style"

    fun readTitle(values: Map<String, *>): PlayerTitlePanelStyle = read(
        values,
        "title",
        PlayerTitlePanelStyle.TITLE_DEFAULT,
    )

    fun readClock(values: Map<String, *>): PlayerTitlePanelStyle = read(
        values,
        "clock",
        PlayerTitlePanelStyle.CLOCK_DEFAULT,
    )

    fun write(prefs: SharedPreferences, style: PlayerTitleStyle) {
        val editor = prefs.edit()
        writeTo(editor, style)
        editor.apply()
    }

    fun writeTo(editor: SharedPreferences.Editor, style: PlayerTitleStyle) {
        writePanel(editor, "title", style.titlePanel.normalized())
        writePanel(editor, "clock", style.clockPanel.normalized())
    }

    fun reset(prefs: SharedPreferences) = write(prefs, PlayerTitleStyle.DEFAULT)

    private fun read(
        values: Map<String, *>,
        segment: String,
        defaults: PlayerTitlePanelStyle,
    ): PlayerTitlePanelStyle = PlayerTitlePanelStyle(
        surface = PlayerPanelSurface.fromPref(values.getString(key(segment, "surface"), null)),
        opacityPercent = values.numericInt(key(segment, "panel_opacity"), defaults.opacityPercent),
        accentStrengthPercent = values.numericInt(
            key(segment, "accent_strength"),
            defaults.accentStrengthPercent,
        ),
        gradientEnabled = values.getBoolean(key(segment, "gradient"), defaults.gradientEnabled),
        outlineEnabled = values.getBoolean(key(segment, "outline"), defaults.outlineEnabled),
        outlineWidthDp = values.numericInt(key(segment, "outline_width"), defaults.outlineWidthDp),
        cornerRadiusDp = values.numericInt(key(segment, "corner_radius"), defaults.cornerRadiusDp),
        elevationDp = values.numericInt(key(segment, "elevation"), defaults.elevationDp),
        horizontalPaddingDp = values.numericInt(
            key(segment, "horizontal_padding"),
            defaults.horizontalPaddingDp,
        ),
        verticalPaddingDp = values.numericInt(
            key(segment, "vertical_padding"),
            defaults.verticalPaddingDp,
        ),
        contentSpacingDp = values.numericInt(
            key(segment, "content_spacing"),
            defaults.contentSpacingDp,
        ),
        alignment = enumValueOrDefault(
            values.getString(key(segment, "alignment"), null),
            defaults.alignment,
        ),
        contentAlignment = enumValueOrDefault(
            values.getString(key(segment, "content_alignment"), null),
            defaults.contentAlignment,
        ),
        widthPercent = values.numericInt(key(segment, "width"), defaults.widthPercent),
        verticalOffsetDp = values.numericInt(
            key(segment, "vertical_offset"),
            defaults.verticalOffsetDp,
        ),
        manualPosition = values.getBoolean(
            key(segment, "manual_position"),
            defaults.manualPosition,
        ),
        horizontalOffsetDp = values.numericInt(
            key(segment, "horizontal_offset"),
            defaults.horizontalOffsetDp,
        ),
    ).normalized()

    private fun writePanel(
        editor: SharedPreferences.Editor,
        segment: String,
        style: PlayerTitlePanelStyle,
    ) {
        editor
            .putString(key(segment, "surface"), style.surface.prefValue)
            .putInt(key(segment, "panel_opacity"), style.opacityPercent)
            .putInt(key(segment, "accent_strength"), style.accentStrengthPercent)
            .putBoolean(key(segment, "gradient"), style.gradientEnabled)
            .putBoolean(key(segment, "outline"), style.outlineEnabled)
            .putInt(key(segment, "outline_width"), style.outlineWidthDp)
            .putInt(key(segment, "corner_radius"), style.cornerRadiusDp)
            .putInt(key(segment, "elevation"), style.elevationDp)
            .putInt(key(segment, "horizontal_padding"), style.horizontalPaddingDp)
            .putInt(key(segment, "vertical_padding"), style.verticalPaddingDp)
            .putInt(key(segment, "content_spacing"), style.contentSpacingDp)
            .putString(key(segment, "alignment"), style.alignment.name)
            .putString(key(segment, "content_alignment"), style.contentAlignment.name)
            .putInt(key(segment, "width"), style.widthPercent)
            .putInt(key(segment, "vertical_offset"), style.verticalOffsetDp)
            .putBoolean(key(segment, "manual_position"), style.manualPosition)
            .putInt(key(segment, "horizontal_offset"), style.horizontalOffsetDp)
    }

    private fun key(segment: String, property: String) = "${PREFIX}_${segment}_$property"

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

internal fun PlayerTitleStyle.panelStyleFor(part: PlayerTitlePart): PlayerTitlePanelStyle =
    if (combinedPanels || part.isTitlePart()) titlePanel else clockPanel

internal fun PlayerTitleStyle.withPanelStyle(
    part: PlayerTitlePart,
    panelStyle: PlayerTitlePanelStyle,
): PlayerTitleStyle = if (combinedPanels || part.isTitlePart()) {
    copy(titlePanel = panelStyle.normalized())
} else {
    copy(clockPanel = panelStyle.normalized())
}

internal fun PlayerTitleStyle.adjustPanelOpacity(
    part: PlayerTitlePart,
    delta: Int,
): PlayerTitleStyle {
    val panel = panelStyleFor(part)
    return withPanelStyle(
        part,
        panel.copy(
            opacityPercent = stepPlayerUiValue(
                panel.opacityPercent,
                delta,
                PLAYER_TITLE_PANEL_OPACITY_STEP_PERCENT,
            ),
        ),
    )
}

internal fun MPVActivity.applyPlayerTitlePanelGlass() {
    binding.playerTitleOverlay.applyThemedPanelStyle(playerTitleStyle.titlePanel, this)
    binding.timeInfoPanel.applyThemedPanelStyle(playerTitleStyle.clockPanel, this)
    applyPlayerTitleCombinedPanel()
}

internal fun View.applyThemedPanelStyle(style: PlayerTitlePanelStyle, themedContext: Context) {
    val normalized = style.normalized()
    val accent = themedContext.themedColor(R.attr.mpvAccentHot, R.color.tv_purple_hot)
    val blendRatio = normalized.accentStrengthPercent / MAX_PERCENT.toFloat()
    val startColor = ColorUtils.blendARGB(
        themedContext.themedColor(R.attr.mpvSurfaceAltStart, R.color.tv_surface_alt),
        accent,
        blendRatio,
    )
    val endColor = ColorUtils.blendARGB(
        themedContext.themedColor(R.attr.mpvSurfaceAltEnd, R.color.tv_surface_alt),
        accent,
        blendRatio,
    )
    val strokeColor = themedContext.themedColor(R.attr.mpvStroke, R.color.tv_stroke)
    val colors = when (normalized.surface) {
        PlayerPanelSurface.GLASS -> if (normalized.gradientEnabled) {
            intArrayOf(
                startColor.withOpacity(normalized.opacityPercent),
                endColor.withOpacity(normalized.opacityPercent),
            )
        } else {
            intArrayOf(
                endColor.withOpacity(normalized.opacityPercent),
                endColor.withOpacity(normalized.opacityPercent),
            )
        }
        PlayerPanelSurface.FLAT -> intArrayOf(
            endColor.withOpacity(normalized.opacityPercent),
            endColor.withOpacity(normalized.opacityPercent),
        )
        PlayerPanelSurface.TRANSPARENT -> intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
        cornerRadius = resources.displayMetrics.density * normalized.cornerRadiusDp
        if (normalized.outlineEnabled) {
            setStroke(
                (resources.displayMetrics.density * normalized.outlineWidthDp)
                    .toInt()
                    .coerceAtLeast(1),
                strokeColor,
            )
        }
    }
    elevation = resources.displayMetrics.density * normalized.elevationDp
    val horizontal = dp(normalized.horizontalPaddingDp)
    val vertical = dp(normalized.verticalPaddingDp)
    setPadding(horizontal, vertical, horizontal, vertical)
    applyPanelPosition(normalized)
    (this as? LinearLayout)?.gravity = normalized.contentAlignment.gravityValue()
    (this as? ViewGroup)?.applyContentLayout(
        normalized.contentSpacingDp,
        normalized.contentAlignment,
    )
}

private data class PanelPositionSpec(
    val alignStart: Boolean,
    val alignCenter: Boolean,
    val alignEnd: Boolean,
    val marginStart: Int,
    val marginEnd: Int,
    val topMargin: Int,
    val width: Int,
)

private fun View.resolvePanelPositionSpec(style: PlayerTitlePanelStyle): PanelPositionSpec = PanelPositionSpec(
    alignStart = style.manualPosition || style.alignment == PlayerTitlePanelAlignment.START,
    alignCenter = !style.manualPosition && style.alignment == PlayerTitlePanelAlignment.CENTER,
    alignEnd = !style.manualPosition && style.alignment == PlayerTitlePanelAlignment.END,
    marginStart = when {
        style.manualPosition -> dp(style.horizontalOffsetDp)
        style.alignment == PlayerTitlePanelAlignment.START -> dp(PANEL_EDGE_MARGIN_DP)
        else -> 0
    },
    marginEnd = if (!style.manualPosition && style.alignment == PlayerTitlePanelAlignment.END) {
        dp(PANEL_EDGE_MARGIN_DP)
    } else {
        0
    },
    topMargin = dp(style.verticalOffsetDp),
    width = if (style.widthPercent == PLAYER_TITLE_PANEL_AUTO_WIDTH_PERCENT) {
        ViewGroup.LayoutParams.WRAP_CONTENT
    } else {
        resources.displayMetrics.widthPixels * style.widthPercent / MAX_PERCENT
    },
)

private fun RelativeLayout.LayoutParams.matchesPanelSpec(spec: PanelPositionSpec): Boolean {
    val rules = rules
    val rulesMatch =
        (rules[RelativeLayout.ALIGN_PARENT_START] != 0) == spec.alignStart &&
            (rules[RelativeLayout.CENTER_HORIZONTAL] != 0) == spec.alignCenter &&
            (rules[RelativeLayout.ALIGN_PARENT_END] != 0) == spec.alignEnd
    return rulesMatch &&
        marginStart == spec.marginStart &&
        marginEnd == spec.marginEnd &&
        topMargin == spec.topMargin &&
        width == spec.width
}

private fun View.applyPanelPosition(style: PlayerTitlePanelStyle) {
    val params = layoutParams as? RelativeLayout.LayoutParams ?: return
    val spec = resolvePanelPositionSpec(style)
    if (params.matchesPanelSpec(spec)) return

    params.removeRule(RelativeLayout.ALIGN_PARENT_START)
    params.removeRule(RelativeLayout.ALIGN_PARENT_END)
    params.removeRule(RelativeLayout.CENTER_HORIZONTAL)
    if (spec.alignStart) params.addRule(RelativeLayout.ALIGN_PARENT_START)
    if (spec.alignCenter) params.addRule(RelativeLayout.CENTER_HORIZONTAL)
    if (spec.alignEnd) params.addRule(RelativeLayout.ALIGN_PARENT_END)
    params.marginStart = spec.marginStart
    params.marginEnd = spec.marginEnd
    params.topMargin = spec.topMargin
    params.width = spec.width
    layoutParams = params
    if (style.manualPosition) clampManualPositionToViewport()
}

private fun View.clampManualPositionToViewport() {
    post {
        val parentView = parent as? View ?: return@post
        val current = layoutParams as? RelativeLayout.LayoutParams ?: return@post
        val clampedStart = current.marginStart.coerceIn(
            0,
            (parentView.width - width).coerceAtLeast(0),
        )
        val clampedTop = current.topMargin.coerceIn(
            0,
            (parentView.height - height).coerceAtLeast(0),
        )
        if (current.marginStart == clampedStart && current.topMargin == clampedTop) return@post
        current.marginStart = clampedStart
        current.topMargin = clampedTop
        layoutParams = current
    }
}

private fun PlayerTitlePanelAlignment.gravityValue(): Int = when (this) {
    PlayerTitlePanelAlignment.START -> Gravity.START
    PlayerTitlePanelAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
    PlayerTitlePanelAlignment.END -> Gravity.END
}

private fun ViewGroup.applyContentLayout(
    spacingDp: Int,
    alignment: PlayerTitlePanelAlignment,
) {
    val horizontalGravity = alignment.gravityValue()
    val groups = ArrayDeque<ViewGroup>().apply { add(this@applyContentLayout) }
    while (groups.isNotEmpty()) {
        val group = groups.removeFirst()
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is TextView -> {
                    val targetGravity =
                        (child.gravity and Gravity.VERTICAL_GRAVITY_MASK) or horizontalGravity
                    if (child.gravity != targetGravity) {
                        child.gravity = targetGravity
                    }
                    if (child.textAlignment != View.TEXT_ALIGNMENT_GRAVITY) {
                        child.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                    }
                }
                is ViewGroup -> groups.add(child)
            }
        }
    }
    val spacing = dp(spacingDp)
    var hasVisibleChild = false
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
        val targetTopMargin = if (child.visibility == View.GONE || !hasVisibleChild) 0 else spacing
        if (params.topMargin != targetTopMargin || params.bottomMargin != 0) {
            params.topMargin = targetTopMargin
            params.bottomMargin = 0
            child.layoutParams = params
        }
        if (child.visibility != View.GONE) hasVisibleChild = true
    }
}

private fun View.dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

private fun Int.withOpacity(percent: Int): Int {
    val alpha = COLOR_CHANNEL_MAX * percent / PLAYER_TITLE_MAX_PANEL_OPACITY_PERCENT
    return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
}

private const val PANEL_EDGE_MARGIN_DP = 20
private const val COLOR_CHANNEL_MAX = 255
