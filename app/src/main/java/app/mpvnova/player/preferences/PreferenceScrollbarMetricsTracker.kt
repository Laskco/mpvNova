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

    fun invalidate() {
        observedHeights = IntArray(0)
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

        if (observedHeights.size != itemCount) {
            observedHeights = IntArray(itemCount)
        }
        recordVisibleHeights(lm, itemCount)

        val density = recyclerView.resources.displayMetrics.density
        val defaultHeaderPx = (DEFAULT_HEADER_HEIGHT_DP * density).roundToInt()
        val defaultRowPx = (DEFAULT_ROW_HEIGHT_DP * density).roundToInt()
        val fallbacks = computeFallbackHeights(itemCount, itemAt, defaultHeaderPx, defaultRowPx)

        var totalHeight = 0
        var offsetBeforeFirst = 0
        for (pos in 0 until itemCount) {
            val h = observedHeights[pos].takeIf { it > 0 }
                ?: if (itemAt(pos) is PreferenceCategory) fallbacks.headerPx else fallbacks.rowPx
            if (pos < firstVisible) offsetBeforeFirst += h
            totalHeight += h
        }

        val firstTop = lm.findViewByPosition(firstVisible)?.let(lm::getDecoratedTop)
            ?: recyclerView.paddingTop
        val offset = (offsetBeforeFirst + recyclerView.paddingTop - firstTop).coerceAtLeast(0)
        return StableScrollbarMetrics(range = totalHeight, extent = extent, offset = offset)
    }

    private fun recordVisibleHeights(lm: LinearLayoutManager, itemCount: Int) {
        for (i in 0 until lm.childCount) {
            val child = lm.getChildAt(i) ?: continue
            val pos = lm.getPosition(child)
            if (pos in 0 until itemCount) {
                val h = lm.getDecoratedMeasuredHeight(child)
                if (h > 0) observedHeights[pos] = h
            }
        }
    }

    private fun computeFallbackHeights(
        itemCount: Int,
        itemAt: (Int) -> Preference?,
        defaultHeaderPx: Int,
        defaultRowPx: Int,
    ): FallbackHeights {
        var headerSum = 0
        var headerCount = 0
        var rowSum = 0
        var rowCount = 0
        for (pos in 0 until itemCount) {
            val h = observedHeights[pos]
            if (h > 0) {
                if (itemAt(pos) is PreferenceCategory) {
                    headerSum += h
                    headerCount++
                } else {
                    rowSum += h
                    rowCount++
                }
            }
        }
        return FallbackHeights(
            headerPx = if (headerCount > 0) headerSum / headerCount else defaultHeaderPx,
            rowPx = if (rowCount > 0) rowSum / rowCount else defaultRowPx,
        )
    }

    private data class FallbackHeights(val headerPx: Int, val rowPx: Int)

    private companion object {
        const val DEFAULT_HEADER_HEIGHT_DP = 36f
        const val DEFAULT_ROW_HEIGHT_DP = 72f
    }
}
