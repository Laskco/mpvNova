package app.mpvnova.player

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.max

class TvSmoothScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {
    private var focusScrollAnimator: ObjectAnimator? = null

    override fun requestChildRectangleOnScreen(
        child: View,
        rectangle: Rect,
        _immediate: Boolean,
    ): Boolean {
        val targetRect = Rect(rectangle)
        offsetDescendantRectToMyCoords(child, targetRect)
        val delta = computeScrollDeltaToGetChildRectOnScreen(targetRect)
        if (delta == 0) return false

        val contentHeight = getChildAt(0)?.height ?: 0
        val maxScrollY = max(0, contentHeight + paddingTop + paddingBottom - height)
        val targetY = (scrollY + delta).coerceIn(0, maxScrollY)

        focusScrollAnimator?.cancel()
        focusScrollAnimator = ObjectAnimator.ofInt(this, "scrollY", scrollY, targetY).apply {
            duration = FOCUS_SCROLL_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { awakenScrollBars() }
            start()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        focusScrollAnimator?.cancel()
        focusScrollAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FOCUS_SCROLL_DURATION_MS = 110L
    }
}
