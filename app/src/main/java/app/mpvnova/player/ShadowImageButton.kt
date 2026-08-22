package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.roundToInt

/** Draws a compact shadow behind the icon without allocating during playback UI redraws. */
class ShadowImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.imageButtonStyle,
) : AppCompatImageButton(context, attrs, defStyleAttr) {
    private val shadowOffsetPx = (resources.displayMetrics.density * SHADOW_OFFSET_DP)
        .roundToInt()
        .coerceAtLeast(1)
        .toFloat()
    private var shadowSource: Drawable? = null
    private var shadowDrawable: Drawable? = null

    override fun onDraw(canvas: Canvas) {
        val icon = drawable
        val shadow = shadowFor(icon)
        if (icon != null && shadow != null && icon.alpha > 0) {
            shadow.bounds = icon.bounds
            shadow.level = icon.level
            shadow.state = icon.state
            shadow.alpha = (icon.alpha * SHADOW_ALPHA).toInt()

            val saveCount = canvas.save()
            if (cropToPadding) {
                canvas.clipRect(
                    scrollX + paddingLeft,
                    scrollY + paddingTop,
                    scrollX + width - paddingRight,
                    scrollY + height - paddingBottom,
                )
            }
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat() + shadowOffsetPx)
            canvas.concat(imageMatrix)
            shadow.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
        super.onDraw(canvas)
    }

    private fun shadowFor(icon: Drawable?): Drawable? {
        if (icon == null) return null
        if (shadowSource !== icon) {
            shadowSource = icon
            shadowDrawable = icon.constantState
                ?.newDrawable(resources)
                ?.mutate()
                ?.also { DrawableCompat.setTint(it, Color.BLACK) }
        }
        return shadowDrawable
    }

    private companion object {
        const val SHADOW_ALPHA = 0.55f
        const val SHADOW_OFFSET_DP = 0.75f
    }
}
