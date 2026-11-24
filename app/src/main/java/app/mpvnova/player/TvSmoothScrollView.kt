package app.mpvnova.player

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class TvSmoothScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {
    private val tempRect = Rect()
    private val focusEdgePaddingPx = (FOCUS_EDGE_PADDING_DP * resources.displayMetrics.density).roundToInt()
    private val edgeSnapPx = (EDGE_SNAP_DP * resources.displayMetrics.density).roundToInt()
    private val maxFocusCardHeightPx = (MAX_FOCUS_CARD_HEIGHT_DP * resources.displayMetrics.density).roundToInt()

    private val spring = TvScrollSpring()
    private var targetScrollY = 0
    private var frameScheduled = false
    private var isSpringStepping = false
    private var suppressSuperScroll = false
    private var hasCompletedLayout = false

    private val springFrameRunnable = Runnable { stepSpring() }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        hasCompletedLayout = true
        val maxScrollY = computeMaxScrollY()
        targetScrollY = if (spring.isRunning) {
            targetScrollY.coerceIn(0, maxScrollY)
        } else {
            scrollY.coerceIn(0, maxScrollY)
        }
    }

    override fun arrowScroll(direction: Int): Boolean {
        if (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN) {
            return super.arrowScroll(direction)
        }
        val currentFocused = findFocus()?.takeIf { it !== this }
        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)
        val maxJump = maxScrollAmount
        if (nextFocused != null && isWithinScrollReach(nextFocused, maxJump)) {
            nextFocused.requestFocus(direction)
            return true
        }
        val maxScrollY = computeMaxScrollY()
        val anchorY = currentAnchorY(maxScrollY)
        val remaining = if (direction == View.FOCUS_DOWN) maxScrollY - anchorY else anchorY
        val step = min(maxJump, remaining)
        if (step <= 0) return false
        val targetY = if (direction == View.FOCUS_DOWN) anchorY + step else anchorY - step
        animateSpringTo(targetY.coerceIn(0, maxScrollY))
        return true
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        val focusedTarget = if (hasCompletedLayout && isLaidOut) focused else null
        val prevReveal = if (focusedTarget != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusedTarget.revealOnFocusHint.also { focusedTarget.revealOnFocusHint = false }
        } else {
            null
        }
        suppressSuperScroll = focusedTarget != null
        try {
            super.requestChildFocus(child, focused)
        } finally {
            suppressSuperScroll = false
            if (prevReveal != null && focusedTarget != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusedTarget.revealOnFocusHint = prevReveal
            }
        }
        if (focusedTarget != null) {
            scrollViewIntoView(focusedTarget, explicitRect = null, immediate = false)
        }
    }

    override fun requestChildRectangleOnScreen(
        child: View,
        rectangle: Rect,
        immediate: Boolean,
    ): Boolean {
        val focused = findFocus()?.takeIf { it !== this && it !== getChildAt(0) }
        val targetView = focused ?: child
        val explicitRect = if (focused != null) null else rectangle
        return scrollViewIntoView(targetView, explicitRect, immediate = immediate || !hasCompletedLayout)
    }

    override fun scrollTo(x: Int, y: Int) {
        if (suppressSuperScroll) return
        // ScrollView.onLayout calls scrollTo(mScrollX, mScrollY) on every layout pass; do not
        // kill an active spring for a no-op self-clamp.
        if (!isSpringStepping && (x != scrollX || y != scrollY)) {
            stopSpring()
            targetScrollY = y
        }
        super.scrollTo(x, y)
    }

    override fun computeScroll() {
        if (spring.isRunning) return
        super.computeScroll()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            stopSpring()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun fling(velocityY: Int) {
        stopSpring()
        super.fling(velocityY)
    }

    override fun onDetachedFromWindow() {
        stopSpring()
        hasCompletedLayout = false
        super.onDetachedFromWindow()
    }

    private fun scrollViewIntoView(targetView: View, explicitRect: Rect?, immediate: Boolean): Boolean {
        val contentRoot = getChildAt(0) ?: return false
        val maxScrollY = computeMaxScrollY()
        if (maxScrollY <= 0) return false

        val enclosingCard = findEnclosingFocusCard(contentRoot, targetView)
        if (enclosingCard != null) {
            tempRect.set(0, 0, enclosingCard.width, enclosingCard.height)
            offsetDescendantRectToMyCoords(enclosingCard, tempRect)
        } else {
            if (explicitRect != null) {
                tempRect.set(explicitRect)
            } else {
                tempRect.set(0, 0, targetView.width, targetView.height)
            }
            offsetDescendantRectToMyCoords(targetView, tempRect)
        }

        val contentSpan = contentRoot.height + paddingTop + paddingBottom
        val viewportHeight = height - paddingTop - paddingBottom
        if (tempRect.height() <= viewportHeight - focusEdgePaddingPx * 2) {
            tempRect.top = max(0, tempRect.top - focusEdgePaddingPx)
            tempRect.bottom = min(contentSpan, tempRect.bottom + focusEdgePaddingPx)
        }

        val anchorY = currentAnchorY(maxScrollY)
        val targetY = resolveTargetScrollY(targetView, anchorY, maxScrollY)
        if (targetY == anchorY && (!spring.isRunning || targetY == scrollY)) return false

        if (immediate) {
            stopSpring()
            targetScrollY = targetY
            super.scrollTo(scrollX, targetY)
            awakenScrollBars()
        } else {
            animateSpringTo(targetY)
        }
        return true
    }

    private fun resolveTargetScrollY(focusedView: View, anchorY: Int, maxScrollY: Int): Int {
        val viewTop = anchorY + paddingTop
        val viewBottom = anchorY + height - paddingBottom
        var targetY = when {
            tempRect.top < viewTop -> tempRect.top - paddingTop
            tempRect.bottom > viewBottom -> tempRect.bottom - (height - paddingBottom)
            else -> anchorY
        }.coerceIn(0, maxScrollY)

        val fitsAtTop = tempRect.bottom <= height - paddingBottom
        val fitsAtBottom = tempRect.top >= maxScrollY + paddingTop
        val movingUp = targetY < anchorY
        val movingDown = targetY > anchorY
        val isTopmost = fitsAtTop &&
            FocusFinder.getInstance().findNextFocus(this, focusedView, View.FOCUS_UP) == null
        val isBottommost = fitsAtBottom &&
            FocusFinder.getInstance().findNextFocus(this, focusedView, View.FOCUS_DOWN) == null

        when {
            isTopmost && (movingUp || targetY <= edgeSnapPx) -> targetY = 0
            isBottommost && (movingDown || maxScrollY - targetY <= edgeSnapPx) -> targetY = maxScrollY
            movingUp && fitsAtTop && targetY <= edgeSnapPx -> targetY = 0
            movingDown && fitsAtBottom && maxScrollY - targetY <= edgeSnapPx -> targetY = maxScrollY
        }
        return targetY
    }

    private fun animateSpringTo(targetY: Int) {
        targetScrollY = targetY
        val delta = (targetY - scrollY).toFloat()
        if (!spring.retarget(delta, SystemClock.uptimeMillis())) return
        scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            postOnAnimation(springFrameRunnable)
        }
    }

    private fun stepSpring() {
        frameScheduled = false
        if (!spring.isRunning) return

        val maxScrollY = computeMaxScrollY()
        targetScrollY = targetScrollY.coerceIn(0, maxScrollY)
        val nowMs = SystemClock.uptimeMillis()
        val stepPx = spring.step(nowMs)
        val desiredY = if (spring.isRunning) {
            (scrollY + stepPx).coerceIn(0, maxScrollY)
        } else {
            targetScrollY
        }

        val hitTopEdge = desiredY == 0 && stepPx < 0
        val hitBottomEdge = desiredY == maxScrollY && stepPx > 0
        if (desiredY != scrollY) {
            applySpringScrollY(desiredY)
        }
        if (hitTopEdge || hitBottomEdge) {
            spring.stop()
        } else if (spring.isRunning) {
            scheduleNextFrame()
        }
    }

    private fun applySpringScrollY(y: Int) {
        isSpringStepping = true
        try {
            super.scrollTo(scrollX, y)
            awakenScrollBars()
        } finally {
            isSpringStepping = false
        }
    }

    private fun stopSpring() {
        if (frameScheduled) {
            removeCallbacks(springFrameRunnable)
            frameScheduled = false
        }
        spring.stop()
    }

    private fun isWithinScrollReach(view: View, delta: Int): Boolean {
        view.getDrawingRect(tempRect)
        offsetDescendantRectToMyCoords(view, tempRect)
        val targetInt = if (spring.isRunning) targetScrollY else scrollY
        val minY = min(scrollY, targetInt)
        val maxY = max(scrollY, targetInt)
        return tempRect.bottom + delta >= minY && tempRect.top - delta <= maxY + height
    }

    private fun computeMaxScrollY(): Int {
        val contentRoot = getChildAt(0) ?: return 0
        return max(0, contentRoot.height + paddingTop + paddingBottom - height)
    }

    private fun currentAnchorY(maxScrollY: Int): Int {
        val raw = if (spring.isRunning) targetScrollY else scrollY
        return raw.coerceIn(0, maxScrollY)
    }

    /**
     * When focus sits on a small control inside a filter/setting card (such as a +/- stepper
     * button inside a 76dp row), expand to the enclosing row card strictly inside [contentRoot]
     * so rounded card edges never clip at the viewport edge.
     */
    private fun findEnclosingFocusCard(contentRoot: View, focused: View): View? {
        val rootGroup = contentRoot as? ViewGroup ?: return null
        if (focused === this || focused === rootGroup) return null
        val viewportLimit = min(maxFocusCardHeightPx, (height - paddingTop - paddingBottom) / 2)
        if (viewportLimit <= 0) return null

        var best: View? = null
        var parent = focused.parent as? ViewGroup
        while (parent != null && parent !== rootGroup && parent !== this) {
            if (parent.height in (focused.height + 1)..viewportLimit) {
                best = parent
            }
            parent = parent.parent as? ViewGroup
        }
        return if (parent === rootGroup) best else null
    }

    private companion object {
        const val FOCUS_EDGE_PADDING_DP = 10f
        const val EDGE_SNAP_DP = 40f
        const val MAX_FOCUS_CARD_HEIGHT_DP = 140f
    }
}
