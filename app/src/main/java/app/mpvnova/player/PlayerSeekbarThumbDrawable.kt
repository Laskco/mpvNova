package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.roundToInt

internal class PlayerSeekbarThumbDrawable(
    context: Context,
    private val shape: PlayerSeekbarThumbShape,
    private val glowEnabled: Boolean,
    centerColor: PlayerSeekbarThumbColor,
    sizeDp: Int,
) : Drawable() {
    private val sizePx = (sizeDp * context.resources.displayMetrics.density).roundToInt()
    private val strokePx = (THUMB_STROKE_DP * context.resources.displayMetrics.density)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HALO_COLOR }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHADOW_COLOR }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvAccent,
            ContextCompat.getColor(context, R.color.tv_purple_hot),
        )
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = when (centerColor) {
            PlayerSeekbarThumbColor.APP_COLOR -> AppearanceTheme.resolveColor(
                context,
                R.attr.mpvAccent,
                ContextCompat.getColor(context, R.color.tv_purple_hot),
            )
            PlayerSeekbarThumbColor.BLACK -> Color.BLACK
            else -> appearanceColorChoices.firstOrNull {
                it.value == centerColor.appearanceValue
            }?.color ?: Color.WHITE
        }
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvAccentDeep,
            ContextCompat.getColor(context, R.color.tv_purple_deep),
        )
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    private var drawableAlpha = MAX_ALPHA
    private val diamondPath = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (shape == PlayerSeekbarThumbShape.DIAMOND) {
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val radius = min(bounds.width(), bounds.height()) * DIAMOND_RADIUS_FRACTION
            diamondPath.rewind()
            diamondPath.moveTo(centerX, centerY - radius)
            diamondPath.lineTo(centerX + radius, centerY)
            diamondPath.lineTo(centerX, centerY + radius)
            diamondPath.lineTo(centerX - radius, centerY)
            diamondPath.close()
        }
    }

    override fun draw(canvas: Canvas) {
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val diameter = min(bounds.width(), bounds.height()).toFloat()
        if (glowEnabled) {
            canvas.drawCircle(centerX, centerY, diameter * OUTER_RADIUS_FRACTION, haloPaint)
        }
        when (shape) {
            PlayerSeekbarThumbShape.RING -> drawRing(canvas, centerX, centerY, diameter)
            PlayerSeekbarThumbShape.SOLID -> drawSolid(canvas, centerX, centerY, diameter)
            PlayerSeekbarThumbShape.DIAMOND -> drawDiamond(canvas, centerX, centerY, diameter)
            PlayerSeekbarThumbShape.PILL -> drawPill(canvas, centerX, centerY, diameter)
        }
    }

    private fun drawRing(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
        canvas.drawCircle(centerX, centerY, diameter * INNER_RADIUS_FRACTION, centerPaint)
        canvas.drawCircle(centerX, centerY, diameter * INNER_RADIUS_FRACTION, strokePaint)
    }

    private fun drawSolid(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
        if (glowEnabled) {
            canvas.drawCircle(
                centerX,
                centerY + diameter * SHADOW_OFFSET_FRACTION,
                diameter * SOLID_OUTER_RADIUS_FRACTION,
                shadowPaint,
            )
        }
        canvas.drawCircle(centerX, centerY, diameter * SOLID_OUTER_RADIUS_FRACTION, accentPaint)
        canvas.drawCircle(centerX, centerY, diameter * SOLID_INNER_RADIUS_FRACTION, centerPaint)
    }

    private fun drawDiamond(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
        if (diamondPath.isEmpty) {
            val radius = diameter * DIAMOND_RADIUS_FRACTION
            diamondPath.moveTo(centerX, centerY - radius)
            diamondPath.lineTo(centerX + radius, centerY)
            diamondPath.lineTo(centerX, centerY + radius)
            diamondPath.lineTo(centerX - radius, centerY)
            diamondPath.close()
        }
        if (glowEnabled) {
            val save = canvas.save()
            canvas.translate(0f, diameter * SHADOW_OFFSET_FRACTION)
            canvas.drawPath(diamondPath, shadowPaint)
            canvas.restoreToCount(save)
        }
        canvas.drawPath(diamondPath, centerPaint)
        canvas.drawPath(diamondPath, strokePaint)
    }

    private fun drawPill(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
        val halfWidth = diameter * PILL_HALF_WIDTH_FRACTION
        val halfHeight = diameter * PILL_HALF_HEIGHT_FRACTION
        val left = centerX - halfWidth
        val top = centerY - halfHeight
        val right = centerX + halfWidth
        val bottom = centerY + halfHeight
        canvas.drawRoundRect(left, top, right, bottom, halfWidth, halfWidth, centerPaint)
        canvas.drawRoundRect(left, top, right, bottom, halfWidth, halfWidth, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, MAX_ALPHA)
        haloPaint.alpha = HALO_ALPHA * drawableAlpha / MAX_ALPHA
        shadowPaint.alpha = SHADOW_ALPHA * drawableAlpha / MAX_ALPHA
        accentPaint.alpha = drawableAlpha
        centerPaint.alpha = drawableAlpha
        strokePaint.alpha = drawableAlpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        haloPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
        accentPaint.colorFilter = colorFilter
        centerPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    private companion object {
        const val MAX_ALPHA = 255
        const val THUMB_STROKE_DP = 2f
        const val HALO_COLOR = 0x70E0E4E8
        const val SHADOW_COLOR = 0x70000000
        const val HALO_ALPHA = 0x70
        const val SHADOW_ALPHA = 0x70
        const val OUTER_RADIUS_FRACTION = 0.48f
        const val INNER_RADIUS_FRACTION = 0.29f
        const val SOLID_OUTER_RADIUS_FRACTION = 0.40f
        const val SOLID_INNER_RADIUS_FRACTION = 0.31f
        const val SHADOW_OFFSET_FRACTION = 0.06f
        const val DIAMOND_RADIUS_FRACTION = 0.34f
        const val PILL_HALF_WIDTH_FRACTION = 0.20f
        const val PILL_HALF_HEIGHT_FRACTION = 0.40f
    }
}
