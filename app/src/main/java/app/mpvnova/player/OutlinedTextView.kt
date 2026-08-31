package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView

private object OutlinedTextPainter {
    private const val SHADOW_RADIUS_DP = 1.25f
    private const val SHADOW_OFFSET_Y_DP = 0.75f
    private const val SHADOW_COLOR = 0xB3000000.toInt()

    fun draw(view: TextView, effects: OutlinedTextEffects, drawText: () -> Unit) {
        val paint = view.paint
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        paint.isDither = true
        val fillColors = view.textColors
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        val originalStrokeJoin = paint.strokeJoin
        val density = view.resources.displayMetrics.density

        if (effects.outlineWidthDp > 0f && Color.alpha(effects.outlineColor) > 0) {
            view.setTextColor(effects.outlineColor)
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (density * effects.outlineWidthDp).coerceAtLeast(1f)
            paint.strokeJoin = Paint.Join.ROUND
            drawText()
        }

        view.setTextColor(fillColors)
        view.setShadowLayer(
            density * effects.shadowRadiusDp,
            0f,
            density * effects.shadowOffsetYDp,
            effects.shadowColor,
        )
        paint.style = Paint.Style.FILL
        drawText()

        view.setTextColor(fillColors)
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
        paint.strokeJoin = originalStrokeJoin
    }

    fun applyShadow(view: TextView) {
        val density = view.resources.displayMetrics.density
        view.setShadowLayer(
            density * SHADOW_RADIUS_DP,
            0f,
            density * SHADOW_OFFSET_Y_DP,
            SHADOW_COLOR,
        )
    }
}

internal data class OutlinedTextEffects(
    val outlineWidthDp: Float,
    val outlineColor: Int,
    val shadowRadiusDp: Float,
    val shadowOffsetYDp: Float,
    val shadowColor: Int,
)

class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private var suppressInvalidation = false
    internal var textEffects: OutlinedTextEffects? = null
        private set

    init {
        OutlinedTextPainter.applyShadow(this)
    }

    internal fun setTextEffects(
        outlineWidthDp: Float,
        outlineColor: Int,
        shadowRadiusDp: Float,
        shadowOffsetYDp: Float,
        shadowColor: Int,
    ) {
        val effects = OutlinedTextEffects(
            outlineWidthDp = outlineWidthDp,
            outlineColor = outlineColor,
            shadowRadiusDp = shadowRadiusDp,
            shadowOffsetYDp = shadowOffsetYDp,
            shadowColor = shadowColor,
        )
        if (textEffects == effects) return
        textEffects = effects
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val effects = textEffects
        if (effects == null) {
            super.onDraw(canvas)
            return
        }
        suppressInvalidation = true
        try {
            OutlinedTextPainter.draw(this, effects) { super.onDraw(canvas) }
        } finally {
            suppressInvalidation = false
        }
    }

    override fun invalidate() {
        if (!suppressInvalidation) super.invalidate()
    }
}

class OutlinedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle,
) : AppCompatButton(context, attrs, defStyleAttr) {
    init {
        OutlinedTextPainter.applyShadow(this)
    }
}

class OutlinedAppCompatViewInflater : AppCompatViewInflater() {
    override fun createTextView(context: Context, attrs: AttributeSet): AppCompatTextView =
        OutlinedTextView(context, attrs).also(UiFont::applyToTextView)

    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        OutlinedButton(context, attrs).also(UiFont::applyToTextView)

    override fun createEditText(context: Context, attrs: AttributeSet): AppCompatEditText =
        AppCompatEditText(context, attrs).also(UiFont::applyToTextView)

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
        AppCompatCheckBox(context, attrs).also(UiFont::applyToTextView)

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
        AppCompatRadioButton(context, attrs).also(UiFont::applyToTextView)

    override fun createImageView(context: Context, attrs: AttributeSet): AppCompatImageView =
        AppCompatImageView(context, attrs)

    override fun createImageButton(context: Context, attrs: AttributeSet): AppCompatImageButton =
        AppCompatImageButton(context, attrs)
}

internal fun TextView.applyUiTextShadow() = OutlinedTextPainter.applyShadow(this)
