package app.mpvnova.player

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
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
private const val VIEW_TYPE_OPTION = 5
private const val PREF_ROW_OFF_ALPHA = 0.55f
private const val PREF_ROW_DISABLED_ALPHA = 0.4f
private const val PREF_ROW_VALUE_WIDTH_DP = 48f
private const val OPTION_ROW_VALUE_MIN_WIDTH_DP = 72f
private const val OPTION_ROW_VALUE_MAX_WIDTH_DP = 112f

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
    private var boundRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        boundRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        boundRecyclerView = null
    }

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
        is PlayerDrawerRow.Option -> VIEW_TYPE_OPTION
        is PlayerDrawerRow.Spacer -> VIEW_TYPE_SPACER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_BUTTON -> ButtonHolder(DrawerRowFullButtonBinding.inflate(inflater, parent, false))
            VIEW_TYPE_BUTTON_PAIR -> ButtonPairHolder(DrawerRowTwoButtonsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_STATS -> StatsHolder(DrawerRowStatsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_PREFERENCE -> PreferenceHolder(DrawerPrefRowBinding.inflate(inflater, parent, false))
            VIEW_TYPE_OPTION -> OptionHolder(DrawerPrefRowBinding.inflate(inflater, parent, false))
            else -> SpacerHolder(DrawerRowSpacerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PlayerDrawerRow.Button -> (holder as ButtonHolder).bind(row.button)
            is PlayerDrawerRow.ButtonPair -> (holder as ButtonPairHolder).bind(row)
            PlayerDrawerRow.Stats -> (holder as StatsHolder).bind()
            is PlayerDrawerRow.Preference -> (holder as PreferenceHolder).bind(row.preference)
            is PlayerDrawerRow.Option -> (holder as OptionHolder).bind(row.option)
            is PlayerDrawerRow.Spacer -> (holder as SpacerHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = rows.size

    // Refresh the disabled (greyed) state of any row gated on changedKey. A visible row is updated
    // in place on its live ViewHolder — NOT via notifyItemChanged — so the change animation can't
    // steal D-pad focus from the row the user just toggled. Off-screen rows (never focused) fall
    // back to a normal rebind.
    private fun refreshRowsDisabledBy(changedKey: String) {
        rows.forEachIndexed { index, row ->
            if (row !is PlayerDrawerRow.Preference || row.preference.disabledWhenOnKey != changedKey)
                return@forEachIndexed
            val holder = boundRecyclerView?.findViewHolderForAdapterPosition(index)
            if (holder is PreferenceHolder) holder.refreshValueAndDisabledState()
            else notifyItemChanged(index)
        }
    }

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
        private var boundPreference: PlayerDrawerPreference? = null

        fun bind(preference: PlayerDrawerPreference) = with(binding) {
            boundPreference = preference
            prefRowTitle.setText(preference.titleRes)
            prefRowSummary.setText(
                if (preference == PlayerDrawerPreference.HI10P_FALLBACK_OTHER_DEVICES &&
                    isNvidiaShieldDevice()
                ) {
                    R.string.pref_hi10p_fallback_other_devices_shield_summary
                } else {
                    preference.summaryRes
                }
            )
            activity.applyPreferenceValueLayout(prefRowValue)
            refreshPrefRowValue(prefRowValue, prefs.rawValue(preference))
            applyDisabledState()
            root.setOnClickListener {
                // Re-read live so an in-place disabled update is respected without a rebind.
                if (isDisabled(preference))
                    return@setOnClickListener
                val newValue = !prefs.rawValue(preference)
                prefs.edit().putBoolean(preference.key, newValue).apply()
                refreshPrefRowValue(prefRowValue, prefs.rawValue(preference))
                activity.handleDrawerPreferenceChange(preference, newValue)
                refreshRowsDisabledBy(preference.key)
            }
        }

        // Update only the greyed-out state on the live view, leaving focus untouched.
        fun applyDisabledState() {
            val preference = boundPreference ?: return
            binding.root.alpha = if (isDisabled(preference)) PREF_ROW_DISABLED_ALPHA else 1f
        }

        fun refreshValueAndDisabledState() {
            val preference = boundPreference ?: return
            refreshPrefRowValue(binding.prefRowValue, prefs.rawValue(preference))
            applyDisabledState()
        }

        private fun isDisabled(preference: PlayerDrawerPreference): Boolean =
            (preference == PlayerDrawerPreference.HI10P_FALLBACK_OTHER_DEVICES &&
                isNvidiaShieldDevice()) ||
                (preference == PlayerDrawerPreference.MINIMAL_SEEKBAR_WHILE_SEEKING &&
                !activity.playerUiCustomization.seekbarVisible) ||
                preference.disabledWhenOnKey?.let { prefs.getBoolean(it, false) } == true
    }

    private inner class OptionHolder(
        private val binding: DrawerPrefRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(option: PlayerDrawerOption) = with(binding) {
            prefRowTitle.setText(option.titleRes)
            prefRowSummary.setText(option.summaryRes)
            prefRowValue.text = activity.drawerOptionValue(option)
            activity.applyOptionValueLayout(prefRowValue)
            prefRowValue.alpha = 1f
            prefRowValue.isSelected = root.hasFocus()
            root.alpha = 1f
            root.setOnFocusChangeListener { _, hasFocus ->
                prefRowValue.isSelected = hasFocus
            }
            root.setOnClickListener { activity.handleDrawerAction(option.action, dismiss) }
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
        is PlayerDrawerRow.Option -> DrawerPrefRowBinding.inflate(inflater, parent, false)
            .also {
                it.prefRowTitle.setText(option.titleRes)
                it.prefRowSummary.setText(option.summaryRes)
                it.prefRowValue.text = "HW+"
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

private fun SharedPreferences.rawValue(preference: PlayerDrawerPreference): Boolean {
    return getBoolean(preference.key, preference.defaultValue)
}

private fun MPVActivity.applyPreferenceValueLayout(valueView: TextView) {
    valueView.updateLayoutParams<LinearLayout.LayoutParams> {
        width = Utils.convertDp(this@applyPreferenceValueLayout, PREF_ROW_VALUE_WIDTH_DP)
    }
    valueView.minWidth = 0
    valueView.maxWidth = Int.MAX_VALUE
    valueView.maxLines = 1
    valueView.ellipsize = null
}

private fun MPVActivity.applyOptionValueLayout(valueView: TextView) {
    val width = Utils.convertDp(this@applyOptionValueLayout, OPTION_ROW_VALUE_MAX_WIDTH_DP)
    valueView.updateLayoutParams<LinearLayout.LayoutParams> {
        this.width = width
    }
    valueView.minWidth = Utils.convertDp(this@applyOptionValueLayout, OPTION_ROW_VALUE_MIN_WIDTH_DP)
    valueView.maxWidth = width
    valueView.maxLines = 1
    valueView.setHorizontallyScrolling(true)
    valueView.ellipsize = TextUtils.TruncateAt.MARQUEE
    valueView.marqueeRepeatLimit = -1
    val availableTextWidth = width - valueView.paddingStart - valueView.paddingEnd
    val overflows = valueView.paint.measureText(valueView.text.toString()) > availableTextWidth
    valueView.gravity = Gravity.CENTER_VERTICAL or if (overflows) Gravity.START else Gravity.END
}

private fun refreshPrefRowValue(valueView: TextView, on: Boolean) {
    valueView.setText(if (on) R.string.status_on else R.string.status_off)
    valueView.alpha = if (on) 1f else PREF_ROW_OFF_ALPHA
}
