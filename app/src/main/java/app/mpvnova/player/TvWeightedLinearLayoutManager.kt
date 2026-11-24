package app.mpvnova.player

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * LinearLayoutManager driven by the exact same critically-damped [TvScrollSpring] physics as
 * [TvSmoothScrollView], so every RecyclerView list across the app shares identical TV scroll
 * weight and momentum while routing through standard [RecyclerView.scrollBy] for scrollbars.
 */
class TvWeightedLinearLayoutManager : LinearLayoutManager {
    private val edgeBufferPx: Int
    private val spring = TvScrollSpring()
    private var attachedRecyclerView: RecyclerView? = null
    private var frameScheduled = false
    private var isSpringStepping = false
    private var lastConsumedDy = 0

    private val springFrameRunnable = Runnable { stepSpring() }
    private val dragCancelListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                stopSpring()
            }
        }
    }

    constructor(context: Context) : super(context) {
        edgeBufferPx = (FOCUS_EDGE_BUFFER_DP * context.resources.displayMetrics.density).roundToInt()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        edgeBufferPx = (FOCUS_EDGE_BUFFER_DP * context.resources.displayMetrics.density).roundToInt()
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        attachedRecyclerView = view
        view.addOnScrollListener(dragCancelListener)
    }

    override fun onDetachedFromWindow(view: RecyclerView, recycler: RecyclerView.Recycler) {
        stopSpring()
        view.removeOnScrollListener(dragCancelListener)
        if (attachedRecyclerView === view) {
            attachedRecyclerView = null
        }
        super.onDetachedFromWindow(view, recycler)
    }

    override fun scrollToPosition(position: Int) {
        stopSpring()
        super.scrollToPosition(position)
    }

    override fun scrollToPositionWithOffset(position: Int, offset: Int) {
        stopSpring()
        super.scrollToPositionWithOffset(position, offset)
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val consumed = super.scrollVerticallyBy(dy, recycler, state)
        if (isSpringStepping) {
            lastConsumedDy = consumed
        }
        return consumed
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean,
    ): Boolean {
        if (!canScrollVertically()) {
            return super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible)
        }
        val bounds = computeTargetBounds(child, rect) ?: return false
        val dy = computeScrollDelta(bounds.top, bounds.bottom)
        if (dy == 0) {
            clampActiveSpringForVisibleChild(bounds.top, bounds.bottom)
            return false
        }
        if (focusedChildVisible && !isFocusedChildVisibleAfterScrolling(parent, dy)) return false

        if (immediate) {
            stopSpring()
            parent.scrollBy(0, dy)
        } else {
            parent.stopScroll()
            if (spring.retarget(dy.toFloat(), SystemClock.uptimeMillis())) {
                scheduleNextFrame(parent)
            }
        }
        return true
    }

    private fun clampActiveSpringForVisibleChild(targetTop: Int, targetBottom: Int) {
        if (!spring.isRunning) return
        val remaining = spring.remainingDistance
        val nowMs = SystemClock.uptimeMillis()
        if (remaining > 0f) {
            val maxDown = max(0, targetTop - paddingTop).toFloat()
            if (remaining > maxDown) {
                spring.retarget(maxDown, nowMs)
            }
        } else if (remaining < 0f) {
            val maxUp = min(0, targetBottom - (height - paddingBottom)).toFloat()
            if (remaining < maxUp) {
                spring.retarget(maxUp, nowMs)
            }
        }
    }

    private fun scheduleNextFrame(parent: RecyclerView) {
        if (!frameScheduled) {
            frameScheduled = true
            parent.postOnAnimation(springFrameRunnable)
        }
    }

    private fun stepSpring() {
        frameScheduled = false
        val rv = attachedRecyclerView ?: run {
            spring.stop()
            return
        }
        if (!spring.isRunning || !rv.isAttachedToWindow) {
            spring.stop()
            return
        }

        val stepPx = spring.step(SystemClock.uptimeMillis())
        if (stepPx != 0) {
            isSpringStepping = true
            lastConsumedDy = 0
            try {
                rv.scrollBy(0, stepPx)
            } finally {
                isSpringStepping = false
            }
            if (lastConsumedDy != stepPx) {
                spring.stop()
                return
            }
        }
        if (spring.isRunning) {
            scheduleNextFrame(rv)
        }
    }

    private fun stopSpring() {
        if (frameScheduled) {
            attachedRecyclerView?.removeCallbacks(springFrameRunnable)
            frameScheduled = false
        }
        spring.stop()
    }

    private fun computeTargetBounds(child: View, rect: Rect): VerticalBounds? {
        val parentTop = paddingTop
        val parentBottom = height - paddingBottom
        val viewportHeight = parentBottom - parentTop
        if (viewportHeight <= 0) return null

        val decoratedTop = getDecoratedTop(child)
        val decoratedBottom = getDecoratedBottom(child)
        val decoratedHeight = decoratedBottom - decoratedTop
        val useWholeRow = decoratedHeight <= viewportHeight / 2

        var targetTop = if (useWholeRow) decoratedTop else child.top + rect.top - child.scrollY
        var targetBottom = if (useWholeRow) decoratedBottom else targetTop + rect.height()

        val pos = getPosition(child)
        val lastPos = itemCount - 1
        // Reveal a non-focusable section header sitting directly above the first focusable row.
        if (pos == 1) {
            val header = findViewByPosition(0)
            if (header != null && !header.isFocusable) {
                val headerTop = getDecoratedTop(header)
                if (targetBottom - headerTop <= viewportHeight) {
                    targetTop = min(targetTop, headerTop)
                }
            }
        }

        val fitsWithBuffer = targetBottom - targetTop + edgeBufferPx * 2 <= viewportHeight
        if (pos > 0 && fitsWithBuffer) targetTop -= edgeBufferPx
        if (pos < lastPos && fitsWithBuffer) targetBottom += edgeBufferPx
        return VerticalBounds(targetTop, targetBottom)
    }

    private fun computeScrollDelta(targetTop: Int, targetBottom: Int): Int {
        val offScreenTop = targetTop - paddingTop
        val offScreenBottom = targetBottom - (height - paddingBottom)
        return when {
            offScreenTop < 0 -> offScreenTop
            offScreenBottom > 0 -> offScreenBottom
            else -> 0
        }
    }

    private fun isFocusedChildVisibleAfterScrolling(parent: RecyclerView, dy: Int): Boolean {
        val focused = parent.focusedChild ?: return false
        val parentTop = paddingTop
        val parentBottom = height - paddingBottom
        val childTop = getDecoratedTop(focused) - dy
        val childBottom = getDecoratedBottom(focused) - dy
        return childBottom > parentTop && childTop < parentBottom
    }

    private data class VerticalBounds(val top: Int, val bottom: Int)

    private companion object {
        const val FOCUS_EDGE_BUFFER_DP = 10f
    }
}
