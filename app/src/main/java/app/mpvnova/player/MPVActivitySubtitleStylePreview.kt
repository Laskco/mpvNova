package app.mpvnova.player

import android.graphics.Color
import android.graphics.Typeface
import java.io.File

private const val SUB_PREVIEW_SHADOW_BLUR_DP = 1.5f
private const val SUB_PREVIEW_SPACING_EM_FACTOR = 0.04f

internal fun MPVActivity.subtitleStylePreviewSpec(): SubtitleStylePreviewView.Spec {
    val density = resources.displayMetrics.density
    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    val bgOn = bgOpacity > 0
    val outlineActive = !bgOn && subStyleEdge != SubtitleEdgeStyle.NONE
    val shadowOn = !bgOn && subStyleEdge == SubtitleEdgeStyle.DROP_SHADOW
    val shadowOffset = (SUBTITLE_SHADOW_SIZE_STEPS[subStyleShadowSizeIndex] * density).toFloat()
    val letterSpacing = (SUBTITLE_SPACING_STEPS[subStyleSpacingIndex] * SUB_PREVIEW_SPACING_EM_FACTOR).toFloat()

    return SubtitleStylePreviewView.Spec(
        text = getString(R.string.sub_editor_sample),
        textColor = subtitleArgb(
            SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].rgb,
            SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex],
        ),
        outlineColor = if (outlineActive) {
            subtitleArgb(SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].rgb, subStyleExtras.outlineOpacity)
        } else {
            Color.TRANSPARENT
        },
        outlineWidthPx = if (outlineActive) {
            SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex].toFloat() * density
        } else {
            0f
        },
        backgroundColor = if (bgOn) {
            subtitleArgb(SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].rgb, bgOpacity)
        } else {
            Color.TRANSPARENT
        },
        shadowColor = if (shadowOn) {
            subtitleArgb(SUBTITLE_COLOR_OPTIONS[subStyleShadowColorIndex].rgb, subStyleExtras.shadowOpacity)
        } else {
            Color.TRANSPARENT
        },
        shadowRadiusPx = if (shadowOn) SUB_PREVIEW_SHADOW_BLUR_DP * density else 0f,
        shadowOffsetPx = if (shadowOn) shadowOffset else 0f,
        blurRadiusPx = (SUBTITLE_BLUR_STEPS[subStyleBlurIndex] * density).toFloat(),
        letterSpacingEm = letterSpacing,
        typeface = subtitleTypefaceFor(subStyleFontFamily, subStyleBold, subStyleItalic),
        fontSize = subtitlePreviewNumber(
            subStyleExtras.fontSize, "sub-font-size", SUBTITLE_EDITOR_DEFAULT_FONT_SIZE,
        ),
        scale = subScaleSteps[subScaleLevel].toFloat(),
        lineSpacing = subtitlePreviewNumber(subStyleExtras.lineSpacing, "sub-line-spacing", 0),
        sideMargin = subtitlePreviewNumber(
            subStyleExtras.sideMargin, "sub-margin-x", SUBTITLE_EDITOR_DEFAULT_SIDE_MARGIN,
        ),
        alignment = subStyleExtras.alignment.takeUnless { it == SubtitleJustify.AUTO }
            ?: SubtitleJustify.entries.firstOrNull { it.mpvValue == subStyleSavedDefaults?.get("sub-align-x") }
            ?: SubtitleJustify.CENTER,
        justify = subStyleJustify,
        positionPercent = subPosSteps[subPosLevel],
    )
}

private fun MPVActivity.subtitlePreviewNumber(custom: Int?, property: String, default: Int): Float =
    custom?.toFloat() ?: subStyleSavedDefaults?.get(property)?.toFloatOrNull()?.takeIf { it.isFinite() }
        ?: default.toFloat()

internal fun MPVActivity.subtitleTypefaceFor(
    family: String,
    bold: Boolean = false,
    italic: Boolean = false,
): Typeface {
    SubtitleTypefaceCache.getResolved(family, bold, italic)?.let { return it }

    val generic = when (family) {
        "", "sans-serif" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        else -> null
    }
    val resolved = if (generic != null) {
        styledTypeface(generic, bold, italic)
    } else {
        val faces = subtitleFontEntries().filter { it.family == family }

        // Prefer the real face whose own flags match the request (the face libass uses), so the
        // preview shows true italic/bold letterforms. Otherwise fall back to the regular file and let
        // Android synthesize, mirroring libass's own oblique/bold fallback.
        val preferredStyle = SubtitleFontTable.Style(bold, italic)
        val exactFace = faces.firstOrNull { it.style == preferredStyle }
            ?.let { typefaceFromFile(it.file) }
        exactFace ?: run {
            val regularStyle = SubtitleFontTable.Style(bold = false, italic = false)
            val regularFace = faces.firstOrNull { it.style == regularStyle } ?: faces.firstOrNull()
            styledTypeface(regularFace?.let { typefaceFromFile(it.file) } ?: Typeface.DEFAULT, bold, italic)
        }
    }
    SubtitleTypefaceCache.putResolved(family, bold, italic, resolved)
    return resolved
}

internal fun invalidateSubtitleTypefaceCache() {
    SubtitleTypefaceCache.clearResolved()
}

private fun typefaceFromFile(file: File): Typeface? =
    SubtitleTypefaceCache.get(file)

private object SubtitleTypefaceCache {
    private const val MAX_ENTRIES = 32
    private data class Key(val path: String, val size: Long, val modified: Long)
    private data class ResolvedKey(val family: String, val bold: Boolean, val italic: Boolean)
    private val entries = LinkedHashMap<Key, Typeface?>()
    private val resolvedEntries = LinkedHashMap<ResolvedKey, Typeface>()

    @Synchronized
    fun get(file: File): Typeface? {
        val key = Key(file.absolutePath, file.length(), file.lastModified())
        if (entries.containsKey(key))
            return entries[key]
        val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull()
        entries[key] = typeface
        while (entries.size > MAX_ENTRIES)
            entries.remove(entries.keys.first())
        return typeface
    }

    @Synchronized
    fun getResolved(family: String, bold: Boolean, italic: Boolean): Typeface? =
        resolvedEntries[ResolvedKey(family, bold, italic)]

    @Synchronized
    fun putResolved(family: String, bold: Boolean, italic: Boolean, typeface: Typeface) {
        resolvedEntries[ResolvedKey(family, bold, italic)] = typeface
        while (resolvedEntries.size > MAX_ENTRIES) {
            resolvedEntries.remove(resolvedEntries.keys.first())
        }
    }

    @Synchronized
    fun clearResolved() {
        resolvedEntries.clear()
    }
}

private fun styledTypeface(base: Typeface, bold: Boolean, italic: Boolean): Typeface {
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
}
