package app.mpvnova.player

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

// A bounded text-style sample, not a replacement for libass's authored ASS rendering.
internal class SubtitleStylePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    data class Spec(
        val text: String,
        val textColor: Int,
        val outlineColor: Int,
        val outlineWidthPx: Float,
        val backgroundColor: Int,
        val shadowColor: Int,
        val shadowRadiusPx: Float,
        val shadowOffsetPx: Float,
        val blurRadiusPx: Float,
        val letterSpacingEm: Float,
        val typeface: Typeface?,
        val fontSize: Float = SUBTITLE_EDITOR_DEFAULT_FONT_SIZE.toFloat(),
        val scale: Float = 1f,
        val lineSpacing: Float = 0f,
        val sideMargin: Float = SUBTITLE_EDITOR_DEFAULT_SIDE_MARGIN.toFloat(),
        val alignment: SubtitleJustify = SubtitleJustify.CENTER,
        val justify: SubtitleJustify = SubtitleJustify.AUTO,
        val positionPercent: Int = 100,
    )

    private var spec: Spec? = null
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var textLayout: StaticLayout? = null
    private var originX = 0f
    private var originY = 0f
    private var effectScale = 1f
    private var reportedHeight = 0
    var onContentHeightChanged: ((Int) -> Unit)? = null

    fun reportContentHeight() {
        reportedHeight = 0
        requestLayout()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSpec(spec: Spec) {
        if (this.spec == spec) return
        this.spec = spec
        textLayout = null
        contentDescription = spec.text
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val layout = spec?.takeIf { measuredWidth > 0 }?.let { buildLayout(it, measuredWidth) }
        textLayout = layout
        val desiredHeight = max(
            (MIN_PREVIEW_HEIGHT_DP * density).toInt(),
            (layout?.height ?: 0) + ceil(2 * PREVIEW_INSET_DP * density).toInt(),
        )
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
        if (reportedHeight != desiredHeight) {
            reportedHeight = desiredHeight
            post { if (reportedHeight == desiredHeight) onContentHeightChanged?.invoke(desiredHeight) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec ?: return
        if (width <= 0 || height <= 0 || s.text.isBlank()) return
        val layout = textLayout ?: buildLayout(s, width).also { textLayout = it }
        val inset = PREVIEW_INSET_DP * resources.displayMetrics.density
        originY = inset + (height - 2 * inset - layout.height).coerceAtLeast(0f) *
            s.positionPercent.coerceIn(MIN_PERCENT, MAX_PERCENT) / MAX_PERCENT.toFloat()
        val save = canvas.save()
        canvas.translate(originX, originY)
        drawBackground(canvas, layout, s)
        paint.clearShadowLayer()
        paint.maskFilter = if (s.blurRadiusPx > 0f) {
            BlurMaskFilter(s.blurRadiusPx * effectScale, BlurMaskFilter.Blur.NORMAL)
        } else null
        if (s.outlineWidthPx > 0f && Color.alpha(s.outlineColor) > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s.outlineWidthPx * 2f * effectScale
            paint.color = s.outlineColor
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = s.textColor
        if (s.shadowRadiusPx > 0f) {
            paint.setShadowLayer(
                s.shadowRadiusPx * effectScale, 0f, s.shadowOffsetPx * effectScale, s.shadowColor,
            )
        }
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun buildLayout(s: Spec, viewportWidth: Int): StaticLayout {
        val density = resources.displayMetrics.density
        val inset = PREVIEW_INSET_DP * density
        val margin = (s.sideMargin / REFERENCE_WIDTH * viewportWidth)
            .coerceIn(0f, viewportWidth / MAX_MARGIN_DIVISOR)
        val availableWidth = (viewportWidth - 2 * max(inset, margin)).toInt().coerceAtLeast(1)
        paint.typeface = s.typeface ?: Typeface.DEFAULT
        paint.letterSpacing = s.letterSpacingEm
        val spacing = s.lineSpacing * density * BASE_TEXT_DP / SUBTITLE_EDITOR_DEFAULT_FONT_SIZE
        paint.textSize = (BASE_TEXT_DP * density * s.fontSize / SUBTITLE_EDITOR_DEFAULT_FONT_SIZE * s.scale)
            .coerceAtLeast(1f)
        effectScale = (paint.textSize / (BASE_TEXT_DP * density)).coerceAtMost(1f)
        val boxWidth = (ceil(Layout.getDesiredWidth(s.text, paint)).toInt() + 1).coerceIn(1, availableWidth)
        val alignment = if (s.justify == SubtitleJustify.AUTO) s.alignment else s.justify
        val result = StaticLayout.Builder.obtain(s.text, 0, s.text.length, paint, boxWidth)
            .setAlignment(when (alignment) {
                SubtitleJustify.LEFT -> Layout.Alignment.ALIGN_NORMAL
                SubtitleJustify.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            })
            .setIncludePad(false)
            .setLineSpacing(spacing, 1f)
            .build()
        originX = when (s.alignment) {
            SubtitleJustify.LEFT -> max(inset, margin)
            SubtitleJustify.RIGHT -> viewportWidth - max(inset, margin) - result.width
            else -> (viewportWidth - result.width) / 2f
        }
        return result
    }

    private fun drawBackground(canvas: Canvas, layout: StaticLayout, s: Spec) {
        if (Color.alpha(s.backgroundColor) == 0) return
        boxPaint.color = s.backgroundColor
        val pad = BOX_PADDING_DP * resources.displayMetrics.density
        for (line in 0 until layout.lineCount) {
            canvas.drawRect(
                layout.getLineLeft(line) - pad, layout.getLineTop(line).toFloat() - pad,
                layout.getLineRight(line) + pad, layout.getLineBottom(line).toFloat() + pad, boxPaint,
            )
        }
    }

    companion object {
        private const val BASE_TEXT_DP = 22f
        private const val MIN_PREVIEW_HEIGHT_DP = 84f
        private const val PREVIEW_INSET_DP = 12f
        private const val BOX_PADDING_DP = 3f
        private const val REFERENCE_WIDTH = 1280f
        private const val MAX_MARGIN_DIVISOR = 3f
    }
}
