package app.mpvnova.player

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.mpvnova.player.databinding.DrawerPrefRowBinding
import app.mpvnova.player.databinding.DrawerRowFullButtonBinding
import app.mpvnova.player.databinding.DrawerRowSpacerBinding
import app.mpvnova.player.databinding.DrawerRowStatsBinding
import app.mpvnova.player.databinding.DrawerRowTwoButtonsBinding

private const val VIEW_TYPE_BUTTON = 0
private const val VIEW_TYPE_BUTTON_PAIR = 1
private const val VIEW_TYPE_STATS = 2
private const val VIEW_TYPE_PREFERENCE = 3
private const val VIEW_TYPE_SPACER = 4
private const val PREF_ROW_OFF_ALPHA = 0.55f

private data class DrawerScrollbarHeightCache(
    val width: Int,
    val rowsHash: Int,
    val rowHeights: List<Int>,
    val contentHeight: Int,
)

private data class DrawerScrollbarMeasureResult(
    val metrics: StableScrollbarMetrics?,
    val cache: DrawerScrollbarHeightCache?,
)

internal class PlayerDrawerAdapter(
    private val activity: MPVActivity,
    private val dismiss: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StableScrollbarMetricsProvider {
    private val rows = mutableListOf<PlayerDrawerRow>()
    private val prefs = getDefaultSharedPreferences(activity.applicationContext)
    private var scrollbarHeightCache: DrawerScrollbarHeightCache? = null

    fun submitRows(newRows: List<PlayerDrawerRow>) {
        val oldSize = rows.size
        scrollbarHeightCache = null
        rows.clear()
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize)
        }
        rows.addAll(newRows)
        if (newRows.isNotEmpty()) {
            notifyItemRangeInserted(0, newRows.size)
        }
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PlayerDrawerRow.Button -> VIEW_TYPE_BUTTON
        is PlayerDrawerRow.ButtonPair -> VIEW_TYPE_BUTTON_PAIR
        PlayerDrawerRow.Stats -> VIEW_TYPE_STATS
        is PlayerDrawerRow.Preference -> VIEW_TYPE_PREFERENCE
        is PlayerDrawerRow.Spacer -> VIEW_TYPE_SPACER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_BUTTON -> ButtonHolder(DrawerRowFullButtonBinding.inflate(inflater, parent, false))
            VIEW_TYPE_BUTTON_PAIR -> ButtonPairHolder(DrawerRowTwoButtonsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_STATS -> StatsHolder(DrawerRowStatsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_PREFERENCE -> PreferenceHolder(DrawerPrefRowBinding.inflate(inflater, parent, false))
            else -> SpacerHolder(DrawerRowSpacerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PlayerDrawerRow.Button -> (holder as ButtonHolder).bind(row.button)
            is PlayerDrawerRow.ButtonPair -> (holder as ButtonPairHolder).bind(row)
            PlayerDrawerRow.Stats -> (holder as StatsHolder).bind()
            is PlayerDrawerRow.Preference -> (holder as PreferenceHolder).bind(row.preference)
            is PlayerDrawerRow.Spacer -> (holder as SpacerHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun stableScrollbarMetrics(recyclerView: RecyclerView): StableScrollbarMetrics? {
        val result = measureDrawerScrollbarMetrics(recyclerView, rows, scrollbarHeightCache)
        scrollbarHeightCache = result.cache
        return result.metrics
    }

    private fun Button.bindAction(button: PlayerDrawerButtonSpec) {
        setText(button.textRes)
        setOnClickListener { activity.handleDrawerAction(button.action, dismiss) }
    }

    private inner class ButtonHolder(
        private val binding: DrawerRowFullButtonBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(button: PlayerDrawerButtonSpec) {
            binding.drawerRowButton.bindAction(button)
        }
    }

    private inner class ButtonPairHolder(
        private val binding: DrawerRowTwoButtonsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PlayerDrawerRow.ButtonPair) = with(binding) {
            drawerRowLeftButton.bindAction(row.left)
            drawerRowRightButton.bindAction(row.right)
        }
    }

    private inner class StatsHolder(
        private val binding: DrawerRowStatsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind() = with(binding) {
            drawerStatsButton.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_TOGGLE, dismiss)
            }
            drawerStatsPage1Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_1, dismiss)
            }
            drawerStatsPage2Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_2, dismiss)
            }
            drawerStatsPage3Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_3, dismiss)
            }
        }
    }

    private inner class PreferenceHolder(
        private val binding: DrawerPrefRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(preference: PlayerDrawerPreference) = with(binding) {
            prefRowTitle.setText(preference.titleRes)
            prefRowSummary.setText(preference.summaryRes)
            refreshPrefRowValue(prefRowValue, prefs.currentValue(preference))
            root.setOnClickListener {
                val newValue = !prefs.currentValue(preference)
                prefs.edit().putBoolean(preference.key, newValue).apply()
                refreshPrefRowValue(prefRowValue, newValue)
                activity.handleDrawerPreferenceChange(preference, newValue)
            }
        }
    }

    private class SpacerHolder(
        private val binding: DrawerRowSpacerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PlayerDrawerRow.Spacer) {
            val density = binding.root.resources.displayMetrics.density
            binding.root.layoutParams = binding.root.layoutParams.apply {
                height = (row.heightDp * density).toInt()
            }
        }
    }
}

private fun measureDrawerScrollbarMetrics(
    recyclerView: RecyclerView,
    rows: List<PlayerDrawerRow>,
    cache: DrawerScrollbarHeightCache?,
): DrawerScrollbarMeasureResult {
    val extent = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
    val width = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
    if (rows.isEmpty() || extent <= 0 || width <= 0) {
        return DrawerScrollbarMeasureResult(metrics = null, cache = cache)
    }

    val rowsHash = rows.hashCode()
    val heightCache = cache
        ?.takeIf { it.width == width && it.rowsHash == rowsHash }
        ?: measureDrawerRowHeights(recyclerView, rows, width, rowsHash)
    val range = heightCache.contentHeight + recyclerView.paddingTop + recyclerView.paddingBottom
    val offset = drawerStableScrollOffset(recyclerView, heightCache.rowHeights)
    return DrawerScrollbarMeasureResult(
        metrics = StableScrollbarMetrics(range = range, extent = extent, offset = offset),
        cache = heightCache,
    )
}

private fun measureDrawerRowHeights(
    parent: RecyclerView,
    rows: List<PlayerDrawerRow>,
    width: Int,
    rowsHash: Int,
): DrawerScrollbarHeightCache {
    val inflater = LayoutInflater.from(parent.context)
    val heights = rows.map { row -> row.measureDrawerRowHeight(parent, inflater, width) }
    return DrawerScrollbarHeightCache(
        width = width,
        rowsHash = rowsHash,
        rowHeights = heights,
        contentHeight = heights.sum(),
    )
}

private fun PlayerDrawerRow.measureDrawerRowHeight(
    parent: RecyclerView,
    inflater: LayoutInflater,
    width: Int,
): Int {
    val view = inflateMeasureRow(parent, inflater)
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
    return view.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
}

private fun PlayerDrawerRow.inflateMeasureRow(parent: RecyclerView, inflater: LayoutInflater): View {
    return when (this) {
        is PlayerDrawerRow.Button -> DrawerRowFullButtonBinding.inflate(inflater, parent, false)
            .also { it.drawerRowButton.setText(button.textRes) }
            .root
        is PlayerDrawerRow.ButtonPair -> DrawerRowTwoButtonsBinding.inflate(inflater, parent, false)
            .also {
                it.drawerRowLeftButton.setText(left.textRes)
                it.drawerRowRightButton.setText(right.textRes)
            }
            .root
        PlayerDrawerRow.Stats -> DrawerRowStatsBinding.inflate(inflater, parent, false).root
        is PlayerDrawerRow.Preference -> DrawerPrefRowBinding.inflate(inflater, parent, false)
            .also {
                it.prefRowTitle.setText(preference.titleRes)
                it.prefRowSummary.setText(preference.summaryRes)
                it.prefRowValue.setText(R.string.status_on)
            }
            .root
        is PlayerDrawerRow.Spacer -> DrawerRowSpacerBinding.inflate(inflater, parent, false)
            .also { binding ->
                val density = parent.resources.displayMetrics.density
                binding.root.layoutParams = binding.root.layoutParams.apply {
                    height = (heightDp * density).toInt()
                }
            }
            .root
    }
}

private fun drawerStableScrollOffset(
    recyclerView: RecyclerView,
    rowHeights: List<Int>,
): Int {
    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
    val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
    return when {
        layoutManager == null -> recyclerView.computeVerticalScrollOffset()
        firstVisible == RecyclerView.NO_POSITION -> 0
        else -> {
            val firstTop = layoutManager.findViewByPosition(firstVisible)?.top ?: recyclerView.paddingTop
            val previousHeight = rowHeights.take(firstVisible).sum()
            previousHeight + recyclerView.paddingTop - firstTop
        }
    }
}

private fun SharedPreferences.currentValue(preference: PlayerDrawerPreference): Boolean {
    return getBoolean(preference.key, preference.defaultValue)
}

private fun refreshPrefRowValue(valueView: TextView, on: Boolean) {
    valueView.setText(if (on) R.string.status_on else R.string.status_off)
    valueView.alpha = if (on) 1f else PREF_ROW_OFF_ALPHA
}
