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
    private const val OUTLINE_WIDTH_DP = 0.5f
    private const val SHADOW_RADIUS_DP = 1.25f
    private const val SHADOW_OFFSET_Y_DP = 0.75f
    private const val OUTLINE_COLOR = 0xB8000000.toInt()
    private const val SHADOW_COLOR = 0xB3000000.toInt()

    private val defaultEffects = OutlinedTextEffects(
        outlineWidthDp = OUTLINE_WIDTH_DP,
        outlineColor = OUTLINE_COLOR,
        shadowRadiusDp = SHADOW_RADIUS_DP,
        shadowOffsetYDp = SHADOW_OFFSET_Y_DP,
        shadowColor = SHADOW_COLOR,
    )

    fun draw(view: TextView, drawText: () -> Unit) {
        val paint = view.paint
        val fillColors = view.textColors
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        val originalStrokeJoin = paint.strokeJoin
        val density = view.resources.displayMetrics.density

        val effects = (view as? OutlinedTextView)?.textEffects ?: defaultEffects

        if (effects.outlineWidthDp > 0f && Color.alpha(effects.outlineColor) > 0) {
            view.setTextColor(effects.outlineColor)
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * effects.outlineWidthDp
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

    internal fun setTextEffects(
        outlineWidthDp: Float,
        outlineColor: Int,
        shadowRadiusDp: Float,
        shadowOffsetYDp: Float,
        shadowColor: Int,
    ) {
        textEffects = OutlinedTextEffects(
            outlineWidthDp = outlineWidthDp,
            outlineColor = outlineColor,
            shadowRadiusDp = shadowRadiusDp,
            shadowOffsetYDp = shadowOffsetYDp,
            shadowColor = shadowColor,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        suppressInvalidation = true
        try {
            OutlinedTextPainter.draw(this) { super.onDraw(canvas) }
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
    private var suppressInvalidation = false

    override fun onDraw(canvas: Canvas) {
        suppressInvalidation = true
        try {
            OutlinedTextPainter.draw(this) { super.onDraw(canvas) }
        } finally {
            suppressInvalidation = false
        }
    }

    override fun invalidate() {
        if (!suppressInvalidation) super.invalidate()
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
        OutlinedImageView(context, attrs)

    override fun createImageButton(context: Context, attrs: AttributeSet): AppCompatImageButton =
        OutlinedImageButton(context, attrs)
}

internal fun TextView.applyUiTextShadow() = OutlinedTextPainter.applyShadow(this)
