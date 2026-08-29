package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
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
        val radius = diameter * DIAMOND_RADIUS_FRACTION
        val path = diamondPath(centerX, centerY, radius)
        if (glowEnabled) {
            canvas.drawPath(
                diamondPath(centerX, centerY + diameter * SHADOW_OFFSET_FRACTION, radius),
                shadowPaint,
            )
        }
        canvas.drawPath(path, centerPaint)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawPill(canvas: Canvas, centerX: Float, centerY: Float, diameter: Float) {
        val halfWidth = diameter * PILL_HALF_WIDTH_FRACTION
        val halfHeight = diameter * PILL_HALF_HEIGHT_FRACTION
        val rect = RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
        canvas.drawRoundRect(rect, halfWidth, halfWidth, centerPaint)
        canvas.drawRoundRect(rect, halfWidth, halfWidth, strokePaint)
    }

    private fun diamondPath(centerX: Float, centerY: Float, radius: Float) = Path().apply {
        moveTo(centerX, centerY - radius)
        lineTo(centerX + radius, centerY)
        lineTo(centerX, centerY + radius)
        lineTo(centerX - radius, centerY)
        close()
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, MAX_ALPHA)
        haloPaint.alpha = HALO_ALPHA * drawableAlpha / MAX_ALPHA
        shadowPaint.alpha = SHADOW_ALPHA * drawableAlpha / MAX_ALPHA
        listOf(accentPaint, centerPaint, strokePaint).forEach { paint -> paint.alpha = drawableAlpha }
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        listOf(haloPaint, shadowPaint, accentPaint, centerPaint, strokePaint).forEach { paint ->
            paint.colorFilter = colorFilter
        }
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
