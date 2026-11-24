package app.mpvnova.player.preferences

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.mpvnova.player.StableScrollbarMetrics
import kotlin.math.roundToInt

/**
 * Tracks observed decorated row heights separately for category headers vs preference rows
 * so the Settings scrollbar thumb neither jumps nor resizes as headers scroll on/off screen.
 */
internal class PreferenceScrollbarMetricsTracker {
    private var observedHeights = IntArray(0)
    private var isHeader = BooleanArray(0)
    private var headerSum = 0
    private var headerCount = 0
    private var rowSum = 0
    private var rowCount = 0

    fun invalidate() {
        observedHeights = IntArray(0)
        isHeader = BooleanArray(0)
        headerSum = 0
        headerCount = 0
        rowSum = 0
        rowCount = 0
    }

    fun compute(
        recyclerView: RecyclerView,
        itemCount: Int,
        itemAt: (Int) -> Preference?,
    ): StableScrollbarMetrics? {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val extent = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        if (itemCount <= 0 || extent <= 0) return null
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null

        ensureCapacity(itemCount, itemAt)
        recordVisibleHeights(lm, itemCount)

        val density = recyclerView.resources.displayMetrics.density
        val fallbackHeader = if (headerCount > 0) {
            headerSum / headerCount
        } else {
            (DEFAULT_HEADER_HEIGHT_DP * density).roundToInt()
        }
        val fallbackRow = if (rowCount > 0) {
            rowSum / rowCount
        } else {
            (DEFAULT_ROW_HEIGHT_DP * density).roundToInt()
        }

        var totalHeight = 0
        var offsetBeforeFirst = 0
        for (pos in 0 until itemCount) {
            val h = observedHeights[pos].takeIf { it > 0 }
                ?: if (isHeader[pos]) fallbackHeader else fallbackRow
            if (pos < firstVisible) offsetBeforeFirst += h
            totalHeight += h
        }

        val firstTop = lm.findViewByPosition(firstVisible)?.let(lm::getDecoratedTop)
            ?: recyclerView.paddingTop
        val offset = (offsetBeforeFirst + recyclerView.paddingTop - firstTop).coerceAtLeast(0)
        return StableScrollbarMetrics(range = totalHeight, extent = extent, offset = offset)
    }

    private fun ensureCapacity(itemCount: Int, itemAt: (Int) -> Preference?) {
        if (observedHeights.size == itemCount) return
        observedHeights = IntArray(itemCount)
        isHeader = BooleanArray(itemCount) { pos -> itemAt(pos) is PreferenceCategory }
        headerSum = 0
        headerCount = 0
        rowSum = 0
        rowCount = 0
    }

    private fun recordVisibleHeights(lm: LinearLayoutManager, itemCount: Int) {
        for (i in 0 until lm.childCount) {
            val child = lm.getChildAt(i) ?: continue
            val pos = lm.getPosition(child)
            if (pos in 0 until itemCount) {
                val h = lm.getDecoratedMeasuredHeight(child)
                val prev = observedHeights[pos]
                if (h > 0 && h != prev) {
                    observedHeights[pos] = h
                    updateAggregate(isHeader[pos], prev, h)
                }
            }
        }
    }

    private fun updateAggregate(header: Boolean, previousHeight: Int, newHeight: Int) {
        if (header) {
            if (previousHeight > 0) headerSum -= previousHeight else headerCount++
            headerSum += newHeight
        } else {
            if (previousHeight > 0) rowSum -= previousHeight else rowCount++
            rowSum += newHeight
        }
    }

    private companion object {
        const val DEFAULT_HEADER_HEIGHT_DP = 36f
        const val DEFAULT_ROW_HEIGHT_DP = 72f
    }
}
