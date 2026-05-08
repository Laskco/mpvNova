package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat

/**
 * A SeekBar that draws small tick marks at chapter boundaries on the progress track.
 *
 * Call [setChapters] whenever the chapter list or media duration changes.
 * Chapter times at t=0 are skipped (no marker at the very start of the track).
 */
class ChapterSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyle) {

    // Chapter positions as fractions of duration, in [0, 1], excluding 0.0
    private var chapterFractions: FloatArray = FloatArray(0)
    private var dpadSelected = false

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private val density: Float get() = resources.displayMetrics.density

    /**
     * Update the chapter markers drawn on the track.
     *
     * @param chapterTimes  list of chapter start times in seconds
     * @param duration      total media duration in seconds (> 0)
     */
    fun setChapters(chapterTimes: List<Double>, duration: Double) {
        chapterFractions = if (duration <= 0.0 || chapterTimes.isEmpty()) {
            FloatArray(0)
        } else {
            chapterTimes
                .filter { it > EDGE_CHAPTER_SKIP_SECONDS && it < duration - EDGE_CHAPTER_SKIP_SECONDS }
                .map { (it / duration).toFloat() }
                .toFloatArray()
        }
        invalidate()
    }

    /** Remove all chapter markers (e.g. when a new file is loaded). */
    fun clearChapters() {
        chapterFractions = FloatArray(0)
        invalidate()
    }

    fun setDpadSelected(selected: Boolean) {
        if (dpadSelected == selected) return
        dpadSelected = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Track spans from paddingLeft to (width - paddingRight).
        // AppCompatSeekBar pads the view by thumbOffset so the thumb isn't clipped.
        val trackLeft  = paddingLeft.toFloat()
        val trackRight = (width - paddingRight).toFloat()
        val trackSpan  = trackRight - trackLeft
        if (trackSpan <= 0f) return

        val centerY     = height / 2f
        val trackHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            maxOf(maxHeight.toFloat(), TRACK_HEIGHT_DP * density)
        else
            TRACK_HEIGHT_DP * density
        val trackHalfH  = trackHeight / 2f
        if (dpadSelected) {
            selectionPaint.strokeWidth = SELECTION_STROKE_DP * density
            val inset = SELECTION_INSET_DP * density
            canvas.drawRoundRect(
                trackLeft - inset,
                centerY - trackHalfH - inset,
                trackRight + inset,
                centerY + trackHalfH + inset,
                SELECTION_CORNER_RADIUS_DP * density,
                SELECTION_CORNER_RADIUS_DP * density,
                selectionPaint
            )
        }

        if (chapterFractions.isEmpty()) return

        val markerW     = (MARKER_WIDTH_DP * density)
        val markerH     = (MARKER_HEIGHT_DP * density)
        val halfW       = markerW / 2f
        val halfH       = markerH / 2f
        val cornerR     = MARKER_CORNER_RADIUS_DP * density

        for (fraction in chapterFractions) {
            val cx = trackLeft + fraction * trackSpan
            canvas.drawRoundRect(
                cx - halfW,
                centerY - halfH,
                cx + halfW,
                centerY + halfH,
                cornerR, cornerR,
                markerPaint
            )
        }
    }

    companion object {
        private const val MARKER_COLOR = 0xCCFFFFFF.toInt()
        private const val EDGE_CHAPTER_SKIP_SECONDS = 0.5
        private const val TRACK_HEIGHT_DP = 8f
        private const val SELECTION_STROKE_DP = 2f
        private const val SELECTION_INSET_DP = 3f
        private const val SELECTION_CORNER_RADIUS_DP = 10f
        private const val MARKER_WIDTH_DP = 3f
        private const val MARKER_HEIGHT_DP = 12f
        private const val MARKER_CORNER_RADIUS_DP = 1.5f
    }
}
