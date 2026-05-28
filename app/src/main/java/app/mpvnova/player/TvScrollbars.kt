package app.mpvnova.player

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_THUMB_HEIGHT_DP = 48
private const val SCROLLBAR_FADE_DELAY_MS = 1200L
private const val SCROLLBAR_FADE_DURATION_MS = 240L
// Re-sync passes scheduled after a layout pass: covers the initial layout
// (post), settle-after-measure (100ms), and post-anim reveal (180/300ms).
private const val SYNC_RESCHEDULE_SHORT_MS = 100L
private const val SYNC_RESCHEDULE_MEDIUM_MS = 180L
private const val SYNC_RESCHEDULE_LONG_MS = 300L

internal data class StableScrollbarMetrics(
    val range: Int,
    val extent: Int,
    val offset: Int,
)

internal interface StableScrollbarMetricsProvider {
    fun stableScrollbarMetrics(recyclerView: RecyclerView): StableScrollbarMetrics?
}

@Suppress("TooManyFunctions")
object TvScrollbars {
    private val fadeRunnables = WeakHashMap<View, Runnable>()
    private val lastOffsets = WeakHashMap<View, Int>()

    @JvmStatic
    fun bind(scrollView: ScrollView, thumb: View) {
        scrollView.isVerticalScrollBarEnabled = false
        lastOffsets[scrollView] = scrollView.scrollY
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val offset = scrollView.scrollY
            updateScrollViewThumb(
                thumb = thumb,
                scrollView = scrollView,
                reveal = lastOffsets[scrollView]?.let { it != offset } == true
            )
            lastOffsets[scrollView] = offset
        }
        scrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateScrollViewThumb(thumb, scrollView, reveal = false)
        }
        revealAfterLayout(scrollView, thumb)
    }

    @JvmStatic
    fun bind(recyclerView: RecyclerView, thumb: View) {
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateRecyclerThumb(thumb, recyclerView, reveal = dy != 0)
            }
        })
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRecyclerThumb(thumb, recyclerView, reveal = false)
        }
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    updateRecyclerThumb(thumb, recyclerView, reveal = false)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
        )
        revealAfterLayout(recyclerView, thumb)
    }

    @JvmStatic
    fun refresh(scrollView: View, thumb: View) {
        when (scrollView) {
            is RecyclerView -> updateRecyclerThumb(thumb, scrollView, reveal = true)
            is ScrollView -> updateScrollViewThumb(thumb, scrollView, reveal = true)
        }
    }

    @JvmStatic
    fun sync(scrollView: View, thumb: View) {
        when (scrollView) {
            is RecyclerView -> updateRecyclerThumb(thumb, scrollView, reveal = false)
            is ScrollView -> {
                updateScrollViewThumb(thumb, scrollView, reveal = false)
                lastOffsets[scrollView] = scrollView.scrollY
            }
        }
    }

    @JvmStatic
    fun syncAfterLayout(scrollView: View, thumb: View) {
        thumb.post { sync(scrollView, thumb) }
        thumb.postDelayed({ sync(scrollView, thumb) }, SYNC_RESCHEDULE_SHORT_MS)
        thumb.postDelayed({ sync(scrollView, thumb) }, SYNC_RESCHEDULE_LONG_MS)
    }

    @JvmStatic
    fun revealAfterLayout(scrollView: View, thumb: View) {
        thumb.post { sync(scrollView, thumb) }
        thumb.postDelayed({ sync(scrollView, thumb) }, SYNC_RESCHEDULE_SHORT_MS)
        thumb.postDelayed({ refresh(scrollView, thumb) }, SYNC_RESCHEDULE_MEDIUM_MS)
    }

    private fun updateScrollViewThumb(thumb: View, scrollView: ScrollView, reveal: Boolean) {
        val child = scrollView.getChildAt(0) ?: run {
            hideThumb(thumb)
            return
        }
        val extent = scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
        val range = child.bottom
        val maxOffset = max(0, range - extent)
        updateThumb(
            thumb = thumb,
            range = range,
            extent = extent,
            offset = scrollView.scrollY.coerceIn(0, maxOffset),
            maxOffset = maxOffset,
            isScrollable = scrollView.canScrollVertically(-1) || scrollView.canScrollVertically(1),
            reveal = reveal
        )
    }

    private fun updateRecyclerThumb(thumb: View, recyclerView: RecyclerView, reveal: Boolean) {
        val stableMetrics = (recyclerView.adapter as? StableScrollbarMetricsProvider)
            ?.stableScrollbarMetrics(recyclerView)
        val range = stableMetrics?.range ?: recyclerView.computeVerticalScrollRange()
        val extent = stableMetrics?.extent ?: recyclerView.computeVerticalScrollExtent()
        val offset = stableMetrics?.offset ?: recyclerView.computeVerticalScrollOffset()
        val maxOffset = max(0, range - extent)
        updateThumb(
            thumb = thumb,
            range = range,
            extent = extent,
            offset = offset.coerceIn(0, maxOffset),
            maxOffset = maxOffset,
            isScrollable = maxOffset > 0 &&
                (recyclerView.canScrollVertically(-1) || recyclerView.canScrollVertically(1) || stableMetrics != null),
            reveal = reveal
        )
    }

    @Suppress("ReturnCount")
    private fun updateThumb(
        thumb: View,
        range: Int,
        extent: Int,
        offset: Int,
        maxOffset: Int,
        isScrollable: Boolean,
        reveal: Boolean
    ) {
        val parent = thumb.parent as? View ?: return
        val margins = thumb.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val trackHeight = parent.height -
            parent.paddingTop -
            parent.paddingBottom -
            margins.topMargin -
            margins.bottomMargin

        @Suppress("ComplexCondition")
        if (trackHeight <= 0 || range <= extent || maxOffset <= 0 || !isScrollable) {
            hideThumb(thumb)
            return
        }

        val density = thumb.resources.displayMetrics.density
        val minHeight = (MIN_THUMB_HEIGHT_DP * density).roundToInt()
        val thumbHeight = max(minHeight, (trackHeight * extent.toFloat() / range).roundToInt())
            .coerceAtMost(trackHeight)
        if (thumb.layoutParams.height != thumbHeight) {
            thumb.layoutParams = thumb.layoutParams.apply { height = thumbHeight }
        }

        val travel = trackHeight - thumbHeight
        thumb.translationY = (travel * offset.toFloat() / maxOffset)
            .coerceIn(0f, travel.toFloat())
        if (reveal) {
            showThenHideThumb(thumb)
        }
    }

    private fun showThenHideThumb(thumb: View) {
        cancelFade(thumb)
        thumb.isVisible = true
        thumb.alpha = 1f

        val fadeRunnable = Runnable {
            fadeRunnables.remove(thumb)
            thumb.animate()
                .alpha(0f)
                .setDuration(SCROLLBAR_FADE_DURATION_MS)
                .withLayer()
                .withEndAction {
                    thumb.isVisible = false
                }
                .start()
        }
        fadeRunnables[thumb] = fadeRunnable
        thumb.postDelayed(fadeRunnable, SCROLLBAR_FADE_DELAY_MS)
    }

    private fun hideThumb(thumb: View) {
        cancelFade(thumb)
        thumb.alpha = 0f
        thumb.isVisible = false
    }

    private fun cancelFade(thumb: View) {
        fadeRunnables.remove(thumb)?.let { thumb.removeCallbacks(it) }
        thumb.animate().cancel()
    }
}
