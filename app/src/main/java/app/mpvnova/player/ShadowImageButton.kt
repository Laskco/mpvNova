package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.roundToInt

private class OutlinedIconPainter(private val view: ImageView) {
    private val outlineOffsetPx = (view.resources.displayMetrics.density * OUTLINE_OFFSET_DP)
        .roundToInt()
        .coerceAtLeast(1)
        .toFloat()
    private val shadowOffsetPx = (view.resources.displayMetrics.density * SHADOW_OFFSET_DP)
        .roundToInt()
        .coerceAtLeast(1)
        .toFloat()
    private var source: Drawable? = null
    private var silhouette: Drawable? = null

    fun draw(canvas: Canvas) {
        val icon = view.drawable
        if (icon == null || icon.alpha <= 0) return
        val decoration = silhouetteFor(icon) ?: return

        decoration.bounds = icon.bounds
        decoration.level = icon.level
        decoration.state = icon.state
        drawAt(canvas, decoration, -outlineOffsetPx, 0f, icon.alpha, OUTLINE_ALPHA)
        drawAt(canvas, decoration, outlineOffsetPx, 0f, icon.alpha, OUTLINE_ALPHA)
        drawAt(canvas, decoration, 0f, -outlineOffsetPx, icon.alpha, OUTLINE_ALPHA)
        drawAt(canvas, decoration, 0f, outlineOffsetPx, icon.alpha, OUTLINE_ALPHA)
        drawAt(canvas, decoration, 0f, shadowOffsetPx, icon.alpha, SHADOW_ALPHA)
    }

    private fun drawAt(
        canvas: Canvas,
        decoration: Drawable,
        offsetX: Float,
        offsetY: Float,
        sourceAlpha: Int,
        alpha: Float,
    ) {
        decoration.alpha = (sourceAlpha * alpha).toInt()
        val saveCount = canvas.save()
        if (view.cropToPadding) {
            canvas.clipRect(
                view.scrollX + view.paddingLeft,
                view.scrollY + view.paddingTop,
                view.scrollX + view.width - view.paddingRight,
                view.scrollY + view.height - view.paddingBottom,
            )
        }
        canvas.translate(
            view.paddingLeft.toFloat() + offsetX,
            view.paddingTop.toFloat() + offsetY,
        )
        canvas.concat(view.imageMatrix)
        decoration.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun silhouetteFor(icon: Drawable): Drawable? {
        if (source !== icon) {
            source = icon
            silhouette = icon.constantState
                ?.newDrawable(view.resources)
                ?.mutate()
                ?.also { DrawableCompat.setTint(it, Color.BLACK) }
        }
        return silhouette
    }

    private companion object {
        const val OUTLINE_ALPHA = 0.52f
        const val SHADOW_ALPHA = 0.48f
        const val OUTLINE_OFFSET_DP = 0.55f
        const val SHADOW_OFFSET_DP = 1.1f
    }
}

/** Draws a crisp silhouette and compact shadow without allocating during UI redraws. */
open class ShadowImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.imageButtonStyle,
) : AppCompatImageButton(context, attrs, defStyleAttr) {
    private val iconPainter = OutlinedIconPainter(this)
    var outlineAndShadowEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (outlineAndShadowEnabled)
            iconPainter.draw(canvas)
        super.onDraw(canvas)
    }
}
