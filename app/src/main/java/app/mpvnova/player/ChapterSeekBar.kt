package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

/**
 * A SeekBar that marks chapter boundaries on the progress track.
 *
 * By default (the normal playback seekbar) chapters are drawn as small tick marks on the platform
 * track. Call [setChapterGapMode] to instead draw the bar as a row of distinct rounded segments
 * split by real gaps at each chapter boundary — used by the minimal seek overlay. In that mode the
 * unfilled part is painted in the theme window colour (black under AMOLED) so the bar stays visible
 * over video, with the accent colour showing progress.
 *
 * Call [setChapters] whenever the chapter list or media duration changes. Chapter times at t=0 are
 * skipped (no marker at the very start of the track).
 */
@Suppress("TooManyFunctions")
class ChapterSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyle) {

    // Chapter positions as fractions of duration, in [0, 1], excluding 0.0
    private var chapterFractions: FloatArray = FloatArray(0)
    private var dpadSelected = false
    // When true, the bar is drawn as segmented chapter pills (minimal seek overlay) instead of the
    // platform track + tick marks.
    private var chapterGapMode = false
    private var chapterMarkersVisible = true
    private var thumbShape: PlayerSeekbarThumbShape? = null
    private var thumbSizeDp = 0
    private var thumbGlowEnabled = true
    private var thumbColor: PlayerSeekbarThumbColor? = null
    private var trackDrawableRes = 0
    private var playedColor: PlayerSeekbarThumbColor? = null
    private var bufferedColor: PlayerSeekbarThumbColor? = null
    private var unplayedColor: PlayerSeekbarThumbColor? = null
    private var chapterMarkerColor: PlayerSeekbarThumbColor? = null
    private var markerShape = PlayerChapterMarkerShape.TICKS
    private var markerSizePercent = DEFAULT_PLAYER_BAR_SCALE_PERCENT
    private var emphasizeCurrentChapter = false

    private val markerPaint = Paint().apply {
        color = MARKER_COLOR
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvAccentHot,
            ContextCompat.getColor(context, R.color.tv_purple_hot)
        )
        style = Paint.Style.STROKE
    }
    // Segmented (gap-mode) bar: unfilled = the translucent panel surface colour (so it's see-through
    // like the rest of the app's panels, black-tinted under pure-black surfaces) but nudged a little
    // more opaque than the panels so the slim bar stays readable over video. Filled = accent.
    private val segmentTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val base = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvSurfaceSoft,
            ContextCompat.getColor(context, R.color.tv_surface_soft)
        )
        val alpha = (Color.alpha(base) * SEGMENT_TRACK_ALPHA_BOOST).roundToInt().coerceAtMost(MAX_ALPHA)
        color = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        style = Paint.Style.FILL
    }
    private val segmentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvAccent,
            ContextCompat.getColor(context, R.color.tv_purple_hot)
        )
        style = Paint.Style.FILL
    }

    private val density: Float get() = resources.displayMetrics.density
    private var trackHeightPx = TRACK_HEIGHT_DP * density
    private val selectionStrokePx = SELECTION_STROKE_DP * density
    private val selectionInsetPx = SELECTION_INSET_DP * density
    private val selectionCornerRadiusPx = SELECTION_CORNER_RADIUS_DP * density
    private val markerWidthPx = MARKER_WIDTH_DP * density
    private val markerHeightPx = MARKER_HEIGHT_DP * density
    private val gapWidthPx = GAP_WIDTH_DP * density

    /**
     * Update the chapter markers drawn on the track.
     *
     * @param chapterTimes  list of chapter start times in seconds
     * @param duration      total media duration in seconds (> 0)
     */
    fun setChapters(chapterTimes: List<Double>, duration: Double) {
        if (!duration.isFinite() || duration <= 0.0 || chapterTimes.isEmpty()) {
            updateChapterFractions(EMPTY_CHAPTER_FRACTIONS)
            return
        }

        val fractions = FloatArray(chapterTimes.size)
        var count = 0
        for (time in chapterTimes) {
            if (time > EDGE_CHAPTER_SKIP_SECONDS && time < duration - EDGE_CHAPTER_SKIP_SECONDS) {
                fractions[count] = (time / duration).toFloat()
                count++
            }
        }
        updateChapterFractions(if (count == 0) EMPTY_CHAPTER_FRACTIONS else fractions.copyOf(count))
    }

    /** Remove all chapter markers (e.g. when a new file is loaded). */
    fun clearChapters() {
        updateChapterFractions(EMPTY_CHAPTER_FRACTIONS)
    }

    fun setDpadSelected(selected: Boolean) {
        if (dpadSelected == selected) return
        dpadSelected = selected
        invalidate()
    }

    /** Changes only the progress track thickness; the thumb keeps its original circular size. */
    fun setTrackStyle(heightDp: Float, @DrawableRes drawableRes: Int, themedContext: Context) {
        val heightPx = (heightDp * density).roundToInt().coerceAtLeast(1)
        var drawableChanged = false
        if (trackDrawableRes != drawableRes) {
            trackDrawableRes = drawableRes
            progressDrawable = ResourcesCompat.getDrawable(
                themedContext.resources,
                drawableRes,
                themedContext.theme,
            )
            drawableChanged = true
            applyTrackColors(themedContext)
        }
        val heightChanged = trackHeightPx.roundToInt() != heightPx
        if (heightChanged) {
            trackHeightPx = heightPx.toFloat()
        }
        if (drawableChanged || heightChanged) {
            requestLayout()
            invalidate()
        }
    }

    internal fun setThumbStyle(
        themedContext: Context,
        shape: PlayerSeekbarThumbShape,
        sizeDp: Int,
        offsetDp: Float,
        glowEnabled: Boolean,
        color: PlayerSeekbarThumbColor,
    ) {
        val sameGeometry = thumbShape == shape && thumbSizeDp == sizeDp
        val sameAppearance = thumbGlowEnabled == glowEnabled && thumbColor == color
        if (sameGeometry && sameAppearance) return
        thumbShape = shape
        thumbSizeDp = sizeDp
        thumbGlowEnabled = glowEnabled
        thumbColor = color
        thumb = PlayerSeekbarThumbDrawable(themedContext, shape, glowEnabled, color, sizeDp)
        thumbOffset = (offsetDp * density).roundToInt()
        requestLayout()
        invalidate()
    }

    internal fun refreshTheme(themedContext: Context) {
        selectionPaint.color = AppearanceTheme.resolveColor(
            themedContext,
            R.attr.mpvAccentHot,
            ContextCompat.getColor(themedContext, R.color.tv_purple_hot),
        )
        val segmentBase = AppearanceTheme.resolveColor(
            themedContext,
            R.attr.mpvSurfaceSoft,
            ContextCompat.getColor(themedContext, R.color.tv_surface_soft),
        )
        val segmentAlpha = (Color.alpha(segmentBase) * SEGMENT_TRACK_ALPHA_BOOST)
            .roundToInt()
            .coerceAtMost(MAX_ALPHA)
        segmentTrackPaint.color = Color.argb(
            segmentAlpha,
            Color.red(segmentBase),
            Color.green(segmentBase),
            Color.blue(segmentBase),
        )
        segmentFillPaint.color = AppearanceTheme.resolveColor(
            themedContext,
            R.attr.mpvAccent,
            ContextCompat.getColor(themedContext, R.color.tv_purple_hot),
        )
        if (trackDrawableRes != 0) {
            progressDrawable = ResourcesCompat.getDrawable(
                themedContext.resources,
                trackDrawableRes,
                themedContext.theme,
            )
            applyTrackColors(themedContext)
        }
        markerPaint.color = chapterMarkerColor?.resolvePlayerBarColor(themedContext) ?: MARKER_COLOR
        val shape = thumbShape
        val color = thumbColor
        if (shape != null && color != null) {
            thumb = PlayerSeekbarThumbDrawable(
                themedContext,
                shape,
                thumbGlowEnabled,
                color,
                thumbSizeDp,
            )
        }
        invalidate()
    }

    fun setChapterMarkersVisible(visible: Boolean) {
        if (chapterMarkersVisible == visible) return
        chapterMarkersVisible = visible
        invalidate()
    }

    internal fun setBarAppearance(style: PlayerUiCustomization, themedContext: Context) {
        val paletteChanged = playedColor != style.seekbarPlayedColor ||
            bufferedColor != style.seekbarBufferedColor || unplayedColor != style.seekbarUnplayedColor
        playedColor = style.seekbarPlayedColor
        bufferedColor = style.seekbarBufferedColor
        unplayedColor = style.seekbarUnplayedColor
        chapterMarkerColor = style.chapterMarkerColor
        markerPaint.color = chapterMarkerColor?.resolvePlayerBarColor(themedContext) ?: MARKER_COLOR
        markerShape = style.chapterMarkerShape
        markerPaint.isAntiAlias = markerShape == PlayerChapterMarkerShape.DOTS
        markerSizePercent = style.chapterMarkerSizePercent.coerceIn(
            MIN_CHAPTER_MARKER_SIZE_PERCENT, MAX_CHAPTER_MARKER_SIZE_PERCENT,
        )
        emphasizeCurrentChapter = style.currentChapterEmphasis
        // Tint in place: replacing the drawable lets SeekBar recalculate its carefully tuned bounds.
        if (paletteChanged) applyTrackColors(themedContext)
        invalidate()
    }

    private fun applyTrackColors(themedContext: Context) {
        val layers = progressDrawable?.mutate() as? LayerDrawable ?: return
        listOf(
            android.R.id.progress to playedColor,
            android.R.id.secondaryProgress to bufferedColor,
            android.R.id.background to unplayedColor,
        ).forEach { (id, color) ->
            layers.findDrawableByLayerId(id)?.let { layer ->
                if (color == null) {
                    layer.setTintList(null)
                } else {
                    layer.setTint(color.resolvePlayerBarColor(themedContext))
                }
            }
        }
    }

    /** Draw the bar as segmented chapter pills split by real gaps (minimal seek overlay). */
    fun setChapterGapMode(enabled: Boolean) {
        if (chapterGapMode == enabled) return
        chapterGapMode = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (chapterGapMode) {
            drawSegmentedBar(canvas)
            return
        }
        drawSelectionOutline(canvas)
        super.onDraw(canvas)
        drawChapterMarkers(canvas)
    }

    /** Draw the focus outline below the platform track and thumb. */
    private fun drawSelectionOutline(canvas: Canvas) {
        if (!dpadSelected) return

        val trackBounds = progressDrawable?.bounds
        val trackCenterY = if (trackBounds != null && !trackBounds.isEmpty) {
            paddingTop + trackBounds.exactCenterY()
        } else {
            height / 2f
        }
        val centerY = trackCenterY + SELECTION_OPTICAL_CENTER_OFFSET_PX
        selectionPaint.strokeWidth = selectionStrokePx
        val strokeInset = selectionStrokePx / 2f
        val outlineLeft = (paddingLeft - selectionInsetPx).coerceAtLeast(strokeInset)
        val outlineRight = (width - paddingRight + selectionInsetPx).coerceAtMost(width - strokeInset)
        val maxHalfHeight = minOf(
            centerY - strokeInset,
            height - strokeInset - centerY,
        ).coerceAtLeast(0f)
        val outlineHalfHeight = (trackHeightPx / 2f + selectionInsetPx).coerceAtMost(maxHalfHeight)
        if (outlineRight <= outlineLeft || outlineHalfHeight <= 0f) return

        canvas.drawRoundRect(
            outlineLeft,
            centerY - outlineHalfHeight,
            outlineRight,
            centerY + outlineHalfHeight,
            selectionCornerRadiusPx,
            selectionCornerRadiusPx,
            selectionPaint
        )
    }

    /** Draw chapter tick marks above the platform track. */
    private fun drawChapterMarkers(canvas: Canvas) {
        if (!chapterMarkersVisible || chapterFractions.isEmpty()) return

        // Track spans from paddingLeft to (width - paddingRight).
        // AppCompatSeekBar pads the view by thumbOffset so the thumb isn't clipped.
        val trackLeft  = paddingLeft.toFloat()
        val trackRight = (width - paddingRight).toFloat()
        val trackSpan  = trackRight - trackLeft
        if (trackSpan <= 0f) return

        drawChapterTicks(canvas, trackLeft, trackSpan, height / 2f)
    }

    private fun drawChapterTicks(canvas: Canvas, trackLeft: Float, trackSpan: Float, centerY: Float) {
        val progressFraction = if (max > 0) progress.toFloat() / max else 0f
        // The opening boundary is intentionally omitted, so emphasize its ending marker instead.
        val currentMarker = chapterFractions.indexOfLast { it <= progressFraction }.coerceAtLeast(0)
        for ((index, fraction) in chapterFractions.withIndex()) {
            val emphasis = if (emphasizeCurrentChapter && index == currentMarker) CURRENT_CHAPTER_EMPHASIS_SCALE else 1f
            val scale = markerSizePercent / MAX_PERCENT.toFloat() * emphasis
            val halfW = markerWidthPx * scale / 2f
            val halfH = (markerHeightPx * scale / 2f).coerceAtMost(height / 2f)
            val cx = trackLeft + fraction * trackSpan
            if (markerShape == PlayerChapterMarkerShape.DOTS) {
                canvas.drawCircle(cx, centerY, (markerWidthPx * scale).coerceAtMost(height / 2f), markerPaint)
            } else {
                canvas.drawRect(cx - halfW, centerY - halfH, cx + halfW, centerY + halfH, markerPaint)
            }
        }
    }

    /**
     * Draw the bar as a row of rounded chapter segments: each chapter is its own pill, separated by
     * a real gap, with the unfilled part in the theme window colour and the played part in accent.
     * With no chapters this is just one full-width segment.
     */
    private fun drawSegmentedBar(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val span = right - left
        if (span <= 0f) return

        val centerY = height / 2f
        val halfH = trackHeightPx / 2f
        val radius = halfH
        val fraction = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
        val progressX = left + fraction * span
        val gapHalf = gapWidthPx / 2f
        val lastIndex = chapterFractions.size

        for (i in 0..lastIndex) {
            val startFrac = if (i == 0) 0f else chapterFractions[i - 1]
            val endFrac = if (i == lastIndex) 1f else chapterFractions[i]
            var segL = left + startFrac * span
            var segR = left + endFrac * span
            if (i > 0) segL += gapHalf
            if (i < lastIndex) segR -= gapHalf
            if (segR <= segL) continue

            canvas.drawRoundRect(
                segL, centerY - halfH, segR, centerY + halfH, radius, radius, segmentTrackPaint
            )
            if (progressX > segL) {
                val fillR = minOf(progressX, segR)
                canvas.drawRoundRect(
                    segL, centerY - halfH, fillR, centerY + halfH, radius, radius, segmentFillPaint
                )
            }
        }
    }

    private fun updateChapterFractions(fractions: FloatArray) {
        if (chapterFractions.contentEquals(fractions))
            return
        chapterFractions = fractions.sortedArray().distinct().toFloatArray()
        invalidate()
    }

    companion object {
        private val EMPTY_CHAPTER_FRACTIONS = FloatArray(0)
        private const val MARKER_COLOR = 0xCCFFFFFF.toInt()
        private const val EDGE_CHAPTER_SKIP_SECONDS = 0.5
        private const val TRACK_HEIGHT_DP = 8f
        private const val SELECTION_STROKE_DP = 2f
        private const val SELECTION_INSET_DP = 3f
        private const val SELECTION_OPTICAL_CENTER_OFFSET_PX = -0.5f
        private const val SELECTION_CORNER_RADIUS_DP = 10f
        private const val MARKER_WIDTH_DP = 3f
        private const val MARKER_HEIGHT_DP = 12f
        private const val CURRENT_CHAPTER_EMPHASIS_SCALE = 1.5f
        private const val GAP_WIDTH_DP = 6f
        private const val SEGMENT_TRACK_ALPHA_BOOST = 1.4f
        private const val MAX_ALPHA = 255
    }
}
