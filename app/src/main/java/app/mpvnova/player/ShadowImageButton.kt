package app.mpvnova.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
    private var bakedBitmap: Bitmap? = null
    private val bakedCanvas = Canvas()
    private val bakedPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrixValues = FloatArray(MATRIX_VALUE_COUNT)
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedPaddingLeft = 0
    private var cachedPaddingTop = 0
    private var cachedPaddingRight = 0
    private var cachedPaddingBottom = 0
    private var cachedLevel = -1
    private var cachedStateHash = 0
    private var cachedMatrixHash = 0
    private var cachedConstantState: Drawable.ConstantState? = null
    private val cachedBounds = Rect()

    fun draw(canvas: Canvas) {
        val icon = view.drawable ?: return
        val iconAlpha = icon.alpha
        if (iconAlpha <= 0) return
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return

        val bitmap = ensureBakedSilhouette(icon, width, height) ?: return
        bakedPaint.alpha = iconAlpha
        canvas.drawBitmap(bitmap, 0f, 0f, bakedPaint)
    }

    fun recycle() {
        bakedCanvas.setBitmap(null)
        bakedBitmap = null
        source = null
        silhouette = null
        cachedConstantState = null
        cachedWidth = 0
        cachedHeight = 0
    }

    private fun ensureBakedSilhouette(
        icon: Drawable,
        width: Int,
        height: Int,
    ): Bitmap? {
        val iconBounds = icon.bounds
        val iconLevel = icon.level
        val iconState = icon.state
        val iconStateHash = iconState.contentHashCode()
        view.imageMatrix.getValues(matrixValues)
        val matrixHash = matrixValues.contentHashCode()
        val constantState = icon.constantState

        if (isCacheValid(icon, constantState, width, height, iconBounds, iconLevel, iconStateHash, matrixHash)) {
            return bakedBitmap
        }
        val decoration = silhouetteFor(icon, constantState) ?: return null
        if (decoration.bounds != iconBounds) decoration.bounds = iconBounds
        if (decoration.level != iconLevel) decoration.level = iconLevel
        if (!decoration.state.contentEquals(iconState)) decoration.state = iconState

        val bitmap = obtainBitmap(width, height)
        bitmap.eraseColor(Color.TRANSPARENT)
        bakedCanvas.setBitmap(bitmap)
        drawSilhouettePasses(bakedCanvas, decoration, width, height)

        cachedWidth = width
        cachedHeight = height
        cachedPaddingLeft = view.paddingLeft
        cachedPaddingTop = view.paddingTop
        cachedPaddingRight = view.paddingRight
        cachedPaddingBottom = view.paddingBottom
        cachedBounds.set(iconBounds)
        cachedLevel = iconLevel
        cachedStateHash = iconStateHash
        cachedMatrixHash = matrixHash
        return bitmap
    }

    @Suppress("LongParameterList")
    private fun isCacheValid(
        icon: Drawable,
        constantState: Drawable.ConstantState?,
        width: Int,
        height: Int,
        iconBounds: Rect,
        iconLevel: Int,
        iconStateHash: Int,
        matrixHash: Int,
    ): Boolean {
        val current = bakedBitmap
        val samePadding = cachedPaddingLeft == view.paddingLeft &&
            cachedPaddingTop == view.paddingTop &&
            cachedPaddingRight == view.paddingRight &&
            cachedPaddingBottom == view.paddingBottom
        return current != null &&
            !current.isRecycled &&
            source === icon &&
            cachedConstantState == constantState &&
            cachedWidth == width &&
            cachedHeight == height &&
            samePadding &&
            cachedBounds == iconBounds &&
            cachedLevel == iconLevel &&
            cachedStateHash == iconStateHash &&
            cachedMatrixHash == matrixHash
    }

    private fun drawSilhouettePasses(
        canvas: Canvas,
        decoration: Drawable,
        width: Int,
        height: Int,
    ) {
        decoration.alpha = (FULL_ALPHA * OUTLINE_ALPHA).toInt()
        drawAt(canvas, decoration, -outlineOffsetPx, 0f, width, height)
        drawAt(canvas, decoration, outlineOffsetPx, 0f, width, height)
        drawAt(canvas, decoration, 0f, -outlineOffsetPx, width, height)
        drawAt(canvas, decoration, 0f, outlineOffsetPx, width, height)

        decoration.alpha = (FULL_ALPHA * SHADOW_ALPHA).toInt()
        drawAt(canvas, decoration, 0f, shadowOffsetPx, width, height)
    }

    private fun obtainBitmap(width: Int, height: Int): Bitmap {
        val current = bakedBitmap
        if (current != null && !current.isRecycled && current.width == width && current.height == height) {
            return current
        }
        bakedCanvas.setBitmap(null)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            bakedBitmap = it
        }
    }

    private fun drawAt(
        canvas: Canvas,
        decoration: Drawable,
        offsetX: Float,
        offsetY: Float,
        width: Int,
        height: Int,
    ) {
        val saveCount = canvas.save()
        if (view.cropToPadding) {
            canvas.clipRect(
                view.paddingLeft,
                view.paddingTop,
                width - view.paddingRight,
                height - view.paddingBottom,
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

    private fun silhouetteFor(
        icon: Drawable,
        constantState: Drawable.ConstantState?,
    ): Drawable? {
        if (source !== icon || cachedConstantState != constantState) {
            source = icon
            cachedConstantState = constantState
            silhouette = constantState
                ?.newDrawable(view.resources)
                ?.mutate()
                ?.also { DrawableCompat.setTint(it, Color.BLACK) }
        }
        return silhouette
    }

    private companion object {
        const val FULL_ALPHA = 255
        const val MATRIX_VALUE_COUNT = 9
        const val OUTLINE_ALPHA = 0.52f
        const val SHADOW_ALPHA = 0.48f
        const val OUTLINE_OFFSET_DP = 0.55f
        const val SHADOW_OFFSET_DP = 1.1f
    }
}

/** Draws a cached icon silhouette without per-frame allocations. */
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
            if (isShown) invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (outlineAndShadowEnabled) iconPainter.draw(canvas)
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        iconPainter.recycle()
        super.onDetachedFromWindow()
    }
}
