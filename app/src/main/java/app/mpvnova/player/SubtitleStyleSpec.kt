package app.mpvnova.player

internal data class SubtitleColorOption(val id: String, val label: String, val rgb: Int)

internal val SUBTITLE_COLOR_OPTIONS = listOf(
    SubtitleColorOption("white", "White", 0xFFFFFF),
    SubtitleColorOption("black", "Black", 0x000000),
    SubtitleColorOption("yellow", "Yellow", 0xFFD60A),
    SubtitleColorOption("amber", "Amber", 0xFF9F0A),
    SubtitleColorOption("red", "Red", 0xFF453A),
    SubtitleColorOption("magenta", "Magenta", 0xFF2D92),
    SubtitleColorOption("violet", "Violet", 0xBF5AF2),
    SubtitleColorOption("blue", "Blue", 0x0A84FF),
    SubtitleColorOption("cyan", "Cyan", 0x64D2FF),
    SubtitleColorOption("green", "Green", 0x30D158),
    SubtitleColorOption("lime", "Lime", 0xB5F23A),
    SubtitleColorOption("gray", "Gray", 0x8E8E93),
)

internal const val SUBTITLE_TEXT_COLOR_DEFAULT_ID = "white"
internal const val SUBTITLE_BORDER_COLOR_DEFAULT_ID = "black"
internal const val SUBTITLE_BG_COLOR_DEFAULT_ID = "black"

internal val SUBTITLE_OPACITY_PERCENT_STEPS = (0..100 step 10).toList().toIntArray()
internal const val DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT = 100
internal const val DEFAULT_SUBTITLE_BG_OPACITY_PERCENT = 0

internal val SUBTITLE_BORDER_SIZE_STEPS = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
internal const val DEFAULT_SUBTITLE_BORDER_INDEX = 3

internal val SUBTITLE_BLUR_STEPS = doubleArrayOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
internal const val DEFAULT_SUBTITLE_BLUR_INDEX = 0

internal val SUBTITLE_SHADOW_SIZE_STEPS = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
internal const val DEFAULT_SUBTITLE_SHADOW_SIZE_INDEX = 2
internal const val SUBTITLE_SHADOW_COLOR_DEFAULT_ID = "black"

internal val SUBTITLE_SPACING_STEPS = doubleArrayOf(0.0, 1.0, 2.0, 4.0, 6.0, 8.0)
internal const val DEFAULT_SUBTITLE_SPACING_INDEX = 0

internal enum class SubtitleJustify(val mpvValue: String) {
    AUTO("auto"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
}

internal val DEFAULT_SUBTITLE_JUSTIFY = SubtitleJustify.AUTO

// No raised/depressed: those need a directional shadow that libass can't do.
internal enum class SubtitleEdgeStyle {
    OUTLINE,
    DROP_SHADOW,
    NONE,
}

internal val DEFAULT_SUBTITLE_EDGE_STYLE = SubtitleEdgeStyle.OUTLINE

internal data class SubtitleFontChoice(val label: String, val family: String)

internal const val SUBTITLE_FONT_DEFAULT_FAMILY = "sans-serif"

internal val SUBTITLE_GENERIC_FONTS = listOf(
    SubtitleFontChoice("Sans serif", "sans-serif"),
    SubtitleFontChoice("Serif", "serif"),
    SubtitleFontChoice("Monospace", "monospace"),
)

internal fun subtitleColorOptionIndex(id: String): Int =
    SUBTITLE_COLOR_OPTIONS.indexOfFirst { it.id == id }.coerceAtLeast(0)

internal fun nearestOpacityIndex(percent: Int): Int =
    SUBTITLE_OPACITY_PERCENT_STEPS.indices.minBy {
        kotlin.math.abs(SUBTITLE_OPACITY_PERCENT_STEPS[it] - percent)
    }

private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_MAX = 255
private const val PERCENT_MAX = 100
private const val PERCENT_HALF = 50

// mpv's alpha is the normal way round here (FF opaque), unlike ASS's own tags.
internal fun mpvSubtitleColor(rgb: Int, opacityPercent: Int): String {
    val opacity = opacityPercent.coerceIn(0, PERCENT_MAX)
    val alpha = (opacity * ALPHA_MAX + PERCENT_HALF) / PERCENT_MAX
    return "#%02X%06X".format(alpha, rgb and RGB_MASK)
}

// ASS stores color as inverted alpha followed by BGR, unlike mpv's AARRGGBB option format.
internal fun assSubtitleColor(rgb: Int, opacityPercent: Int): String {
    val opacity = opacityPercent.coerceIn(0, PERCENT_MAX)
    val alpha = ALPHA_MAX - (opacity * ALPHA_MAX + PERCENT_HALF) / PERCENT_MAX
    val red = rgb shr 16 and 0xFF
    val green = rgb shr 8 and 0xFF
    val blue = rgb and 0xFF
    return "&H%02X%02X%02X%02X".format(alpha, blue, green, red)
}
