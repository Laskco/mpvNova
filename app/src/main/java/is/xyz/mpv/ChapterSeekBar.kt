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
        color = 0xCCFFFFFF.toInt()    // white, slightly transparent
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tv_purple_hot)
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
                .filter { it > 0.5 && it < duration - 0.5 } // skip first & near-end
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
            maxOf(maxHeight.toFloat(), 8f * density)
        else
            8f * density
        val trackHalfH  = trackHeight / 2f
        if (dpadSelected) {
            selectionPaint.strokeWidth = 2f * density
            val inset = 3f * density
            canvas.drawRoundRect(
                trackLeft - inset,
                centerY - trackHalfH - inset,
                trackRight + inset,
                centerY + trackHalfH + inset,
                10f * density,
                10f * density,
                selectionPaint
            )
        }

        if (chapterFractions.isEmpty()) return

        val markerW     = (3f * density)
        val markerH     = (12f * density)
        val halfW       = markerW / 2f
        val halfH       = markerH / 2f
        val cornerR     = 1.5f * density

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
}
