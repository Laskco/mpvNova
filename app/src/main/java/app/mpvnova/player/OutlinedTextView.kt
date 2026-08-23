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

    fun draw(view: TextView, drawText: () -> Unit) {
        val paint = view.paint
        val fillColors = view.textColors
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        val originalStrokeJoin = paint.strokeJoin
        val density = view.resources.displayMetrics.density

        view.setTextColor(OUTLINE_COLOR)
        view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * OUTLINE_WIDTH_DP
        paint.strokeJoin = Paint.Join.ROUND
        drawText()

        view.setTextColor(fillColors)
        view.setShadowLayer(
            density * SHADOW_RADIUS_DP,
            0f,
            density * SHADOW_OFFSET_Y_DP,
            SHADOW_COLOR,
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

class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
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
