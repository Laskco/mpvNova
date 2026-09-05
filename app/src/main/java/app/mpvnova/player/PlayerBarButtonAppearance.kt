package app.mpvnova.player

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.ImageView

internal fun MPVActivity.playerBarButtonBackground(
    view: View,
    style: PlayerUiCustomization,
    original: Drawable?,
): Drawable? {
    val image = view as? ImageView
    // ImageButton originally centered the icon at its intrinsic size, without filling the padding box.
    image?.apply {
        scaleType = ImageView.ScaleType.CENTER
        imageMatrix = null
    }
    val iconPercent = if (view.id == R.id.playBtn) style.primaryPlayIconSizePercent else style.otherIconSizePercent
    val customFocus = style.buttonFocusOutlineWidthDp != DEFAULT_BUTTON_FOCUS_OUTLINE_WIDTH_DP ||
        style.buttonFocusHighlightOpacityPercent != DEFAULT_BUTTON_FOCUS_HIGHLIGHT_OPACITY_PERCENT ||
        style.buttonFocusEnlargementPercent != DEFAULT_PLAYER_BAR_SCALE_PERCENT
    val background = if (customFocus) {
        val focused = playerBarFocusDrawable(style, image != null)
        StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), original)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(android.R.attr.state_selected), focused)
            addState(intArrayOf(), original)
        }
    } else {
        original
    }
    val needsIconTransform = iconPercent != DEFAULT_PLAYER_BAR_SCALE_PERCENT ||
        style.buttonFocusEnlargementPercent != DEFAULT_PLAYER_BAR_SCALE_PERCENT
    if (image == null || background == null || !needsIconTransform) {
        return background
    }
    // Only the image matrix changes. View dimensions, padding and focus navigation stay fixed.
    return PlayerBarIconBackground(background, image, iconPercent, style.buttonFocusEnlargementPercent)
}

private fun MPVActivity.playerBarFocusDrawable(style: PlayerUiCustomization, isIcon: Boolean): Drawable {
    val density = resources.displayMetrics.density
    val opacity = style.buttonFocusHighlightOpacityPercent / MAX_PERCENT.toFloat()
    fun Int.withHighlightOpacity(): Int = Color.argb(
        (Color.alpha(this) * opacity).toInt(), Color.red(this), Color.green(this), Color.blue(this),
    )
    val outer = GradientDrawable().apply {
        shape = if (isIcon) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        cornerRadius = FOCUS_OUTER_RADIUS_DP * density
        setColor(themedColor(R.attr.mpvFocusWashSoft, R.color.tv_surface_soft).withHighlightOpacity())
    }
    val inner = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(FOCUS_GRADIENT_START.withHighlightOpacity(), FOCUS_GRADIENT_END.withHighlightOpacity()),
    ).apply {
        shape = if (isIcon) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        cornerRadius = FOCUS_INNER_RADIUS_DP * density
        setStroke(
            (style.buttonFocusOutlineWidthDp * density).toInt(),
            themedColor(R.attr.mpvStrokeFocus, R.color.tv_stroke_strong),
        )
    }
    val enlargementRange = MAX_BUTTON_FOCUS_ENLARGEMENT_PERCENT - MIN_BUTTON_FOCUS_ENLARGEMENT_PERCENT
    val remainingInsetFraction =
        (MAX_BUTTON_FOCUS_ENLARGEMENT_PERCENT - style.buttonFocusEnlargementPercent) / enlargementRange.toFloat()
    val inset = (FOCUS_INNER_INSET_DP * density * remainingInsetFraction).toInt()
    return LayerDrawable(arrayOf(outer, inner)).apply { setLayerInset(1, inset, inset, inset, inset) }
}

private class PlayerBarIconBackground(
    background: Drawable,
    private val image: ImageView,
    private val iconPercent: Int,
    private val enlargementPercent: Int,
) : LayerDrawable(arrayOf(background)) {
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        updateIconMatrix()
        super.draw(canvas)
    }

    private fun updateIconMatrix() {
        val icon = image.drawable ?: return
        if (icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0) updateIconMatrix(icon)
    }

    private fun updateIconMatrix(icon: Drawable) {
        val contentWidth = (image.width - image.paddingLeft - image.paddingRight).toFloat()
        val contentHeight = (image.height - image.paddingTop - image.paddingBottom).toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) return
        val focused = image.isEnabled && (image.isFocused || image.isSelected || image.isPressed)
        val emphasis = if (focused) enlargementPercent / MAX_PERCENT.toFloat() else 1f
        val density = image.resources.displayMetrics.density
        val maxScale = minOf(
            (image.width - ICON_DRAWING_INSET_DP * density).coerceAtLeast(0f) / icon.intrinsicWidth,
            (image.height - ICON_DRAWING_INSET_DP * density).coerceAtLeast(0f) / icon.intrinsicHeight,
        )
        val scale = (iconPercent / MAX_PERCENT.toFloat() * emphasis).coerceAtMost(maxScale)
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (contentWidth - icon.intrinsicWidth * scale) / 2f,
            (contentHeight - icon.intrinsicHeight * scale) / 2f,
        )
        if (image.scaleType != ImageView.ScaleType.MATRIX) image.scaleType = ImageView.ScaleType.MATRIX
        if (image.imageMatrix != matrix) image.imageMatrix = matrix
    }
}

private const val FOCUS_OUTER_RADIUS_DP = 16f
private const val FOCUS_INNER_RADIUS_DP = 14f
private const val FOCUS_INNER_INSET_DP = 2f
private const val ICON_DRAWING_INSET_DP = 4f
private const val FOCUS_GRADIENT_START = 0x9630353C.toInt()
private const val FOCUS_GRADIENT_END = 0x7813171C
