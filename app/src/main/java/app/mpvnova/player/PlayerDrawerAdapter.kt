package app.mpvnova.player

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
private const val VIEW_TYPE_PREF_ROW = 3
private const val VIEW_TYPE_SPACER = 4
private const val PREF_ROW_POOL_SIZE = 20
private const val BUTTON_ROW_POOL_SIZE = 10
private const val PREF_ROW_OFF_ALPHA = 0.55f
private const val PREF_ROW_DISABLED_ALPHA = 0.4f
private const val PREF_ROW_VALUE_WIDTH_DP = 48f
private const val OPTION_ROW_VALUE_MIN_WIDTH_DP = 72f
private const val OPTION_ROW_VALUE_MAX_WIDTH_DP = 112f

private data class DrawerScrollbarCacheKey(
    val width: Int,
    val rowsHash: Int,
    val fontValue: String,
)

private class DrawerScrollbarHeightCache(
    val key: DrawerScrollbarCacheKey,
    val cumulativeHeights: IntArray,
    val contentHeight: Int,
)

private class DrawerMeasureScratchViews {
    var button: DrawerRowFullButtonBinding? = null
    var buttonPair: DrawerRowTwoButtonsBinding? = null
    var stats: DrawerRowStatsBinding? = null
    var prefRow: DrawerPrefRowBinding? = null
    var spacer: DrawerRowSpacerBinding? = null
}

private const val MAX_SCROLLBAR_CACHE_ENTRIES = 16

internal class PlayerDrawerAdapter(
    private val activity: MPVActivity,
    private val dismiss: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StableScrollbarMetricsProvider {
    private val rows = mutableListOf<PlayerDrawerRow>()
    private var cachedRowsHash = 0
    private val prefs = getDefaultSharedPreferences(activity.applicationContext)
    private val scrollbarHeightCaches = LinkedHashMap<DrawerScrollbarCacheKey, DrawerScrollbarHeightCache>()
    private val measureScratchViews = DrawerMeasureScratchViews()
    private var boundRecyclerView: RecyclerView? = null
    private var cachedNetworkOverrides: Map<NetworkSetting, String>? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        boundRecyclerView = recyclerView
        recyclerView.recycledViewPool.setMaxRecycledViews(VIEW_TYPE_PREF_ROW, PREF_ROW_POOL_SIZE)
        recyclerView.recycledViewPool.setMaxRecycledViews(VIEW_TYPE_BUTTON, BUTTON_ROW_POOL_SIZE)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        boundRecyclerView = null
    }

    fun submitRows(newRows: List<PlayerDrawerRow>) {
        cachedNetworkOverrides = null
        val sameTypes = rows.size == newRows.size && rows.indices.all { index ->
            viewTypeForRow(rows[index]) == viewTypeForRow(newRows[index])
        }
        if (sameTypes && rows.isNotEmpty()) {
            rows.clear()
            rows.addAll(newRows)
            cachedRowsHash = rows.hashCode()
            notifyItemRangeChanged(0, rows.size)
            return
        }
        val oldSize = rows.size
        rows.clear()
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize)
        }
        rows.addAll(newRows)
        cachedRowsHash = rows.hashCode()
        if (newRows.isNotEmpty()) {
            notifyItemRangeInserted(0, newRows.size)
        }
    }

    override fun getItemViewType(position: Int): Int = viewTypeForRow(rows[position])

    private fun viewTypeForRow(row: PlayerDrawerRow): Int = when (row) {
        is PlayerDrawerRow.Button -> VIEW_TYPE_BUTTON
        is PlayerDrawerRow.ButtonPair -> VIEW_TYPE_BUTTON_PAIR
        PlayerDrawerRow.Stats -> VIEW_TYPE_STATS
        is PlayerDrawerRow.Preference,
        is PlayerDrawerRow.Option,
        is PlayerDrawerRow.Network,
        -> VIEW_TYPE_PREF_ROW
        is PlayerDrawerRow.Spacer -> VIEW_TYPE_SPACER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_BUTTON -> ButtonHolder(DrawerRowFullButtonBinding.inflate(inflater, parent, false))
            VIEW_TYPE_BUTTON_PAIR -> ButtonPairHolder(DrawerRowTwoButtonsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_STATS -> StatsHolder(DrawerRowStatsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_PREF_ROW -> PrefRowHolder(DrawerPrefRowBinding.inflate(inflater, parent, false))
            else -> SpacerHolder(DrawerRowSpacerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PlayerDrawerRow.Button -> (holder as ButtonHolder).bind(row.button)
            is PlayerDrawerRow.ButtonPair -> (holder as ButtonPairHolder).bind(row)
            PlayerDrawerRow.Stats -> Unit
            is PlayerDrawerRow.Preference -> (holder as PrefRowHolder).bindPreference(row.preference)
            is PlayerDrawerRow.Option -> (holder as PrefRowHolder).bindOption(row.option)
            is PlayerDrawerRow.Network -> (holder as PrefRowHolder).bindNetwork(row.setting)
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
            if (holder is PrefRowHolder) holder.refreshValueAndDisabledState()
            else notifyItemChanged(index)
        }
    }

    private fun refreshNetworkRows() {
        cachedNetworkOverrides = null
        scrollbarHeightCaches.clear()
        val overrides = currentNetworkOverrides()
        rows.forEachIndexed { index, row ->
            if (row is PlayerDrawerRow.Network) {
                val holder = boundRecyclerView?.findViewHolderForAdapterPosition(index)
                if (holder is PrefRowHolder) holder.bindNetwork(row.setting, overrides) else notifyItemChanged(index)
            }
        }
    }

    private fun currentNetworkOverrides(): Map<NetworkSetting, String> =
        cachedNetworkOverrides ?: activity.networkOverrides().also { cachedNetworkOverrides = it }

    override fun stableScrollbarMetrics(recyclerView: RecyclerView): StableScrollbarMetrics? =
        measureDrawerScrollbarMetrics(recyclerView, rows, cachedRowsHash, scrollbarHeightCaches, measureScratchViews)

    private inner class ButtonHolder(
        private val binding: DrawerRowFullButtonBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundAction: PlayerDrawerAction? = null

        init {
            binding.drawerRowButton.setOnClickListener {
                boundAction?.let { activity.handleDrawerAction(it, dismiss) }
            }
        }

        fun bind(button: PlayerDrawerButtonSpec) {
            boundAction = button.action
            binding.drawerRowButton.setTextIfChanged(activity.getString(button.textRes))
        }
    }

    private inner class ButtonPairHolder(
        private val binding: DrawerRowTwoButtonsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundLeft: PlayerDrawerAction? = null
        private var boundRight: PlayerDrawerAction? = null

        init {
            binding.drawerRowLeftButton.setOnClickListener {
                boundLeft?.let { activity.handleDrawerAction(it, dismiss) }
            }
            binding.drawerRowRightButton.setOnClickListener {
                boundRight?.let { activity.handleDrawerAction(it, dismiss) }
            }
        }

        fun bind(row: PlayerDrawerRow.ButtonPair) = with(binding) {
            boundLeft = row.left.action
            boundRight = row.right.action
            drawerRowLeftButton.setTextIfChanged(activity.getString(row.left.textRes))
            drawerRowRightButton.setTextIfChanged(activity.getString(row.right.textRes))
        }
    }

    private inner class StatsHolder(
        binding: DrawerRowStatsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.drawerStatsButton.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_TOGGLE, dismiss)
            }
            binding.drawerStatsPage1Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_1, dismiss)
            }
            binding.drawerStatsPage2Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_2, dismiss)
            }
            binding.drawerStatsPage3Button.setOnClickListener {
                activity.handleDrawerAction(PlayerDrawerAction.STATS_PAGE_3, dismiss)
            }
        }
    }

    private inner class PrefRowHolder(
        private val binding: DrawerPrefRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var mode = ROW_MODE_PREFERENCE
        private var boundPreference: PlayerDrawerPreference? = null
        private var boundOption: PlayerDrawerOption? = null
        private var boundNetwork: NetworkSetting? = null

        init {
            binding.root.setOnFocusChangeListener { _, focused ->
                if (mode != ROW_MODE_PREFERENCE) {
                    binding.prefRowValue.isSelected = focused
                }
            }
            binding.root.setOnClickListener {
                when (mode) {
                    ROW_MODE_PREFERENCE -> onPreferenceClicked()
                    ROW_MODE_OPTION -> boundOption?.let { activity.handleDrawerAction(it.action, dismiss) }
                    ROW_MODE_NETWORK -> boundNetwork?.let { activity.showNetworkSetting(it, ::refreshNetworkRows) }
                }
            }
        }

        fun bindPreference(preference: PlayerDrawerPreference) = with(binding) {
            mode = ROW_MODE_PREFERENCE
            boundPreference = preference
            boundOption = null
            boundNetwork = null
            prefRowTitle.setTextIfChanged(activity.getString(preference.titleRes))
            prefRowSummary.setTextIfChanged(activity.getString(preference.summaryRes))
            activity.applyPreferenceValueLayout(prefRowValue)
            prefRowValue.isSelected = false
            refreshPrefRowValue(prefRowValue, prefs.rawValue(preference))
            applyDisabledState()
        }

        fun bindOption(option: PlayerDrawerOption) = with(binding) {
            mode = ROW_MODE_OPTION
            boundPreference = null
            boundOption = option
            boundNetwork = null
            prefRowTitle.setTextIfChanged(activity.getString(option.titleRes))
            prefRowSummary.setTextIfChanged(activity.getString(option.summaryRes))
            prefRowValue.setTextIfChanged(activity.drawerOptionValue(option))
            activity.applyOptionValueLayout(prefRowValue)
            prefRowValue.alpha = 1f
            prefRowValue.isSelected = root.hasFocus()
            root.alpha = 1f
        }

        fun bindNetwork(
            setting: NetworkSetting,
            overrides: Map<NetworkSetting, String> = currentNetworkOverrides(),
        ) = with(binding) {
            mode = ROW_MODE_NETWORK
            boundPreference = null
            boundOption = null
            boundNetwork = setting
            prefRowTitle.setTextIfChanged(activity.getString(setting.titleRes))
            prefRowSummary.setTextIfChanged(activity.getString(setting.summaryRes))
            prefRowValue.setTextIfChanged(activity.networkValueLabel(setting, overrides))
            activity.applyOptionValueLayout(prefRowValue)
            prefRowValue.alpha = 1f
            prefRowValue.isSelected = root.hasFocus()
            root.alpha = 1f
        }

        private fun onPreferenceClicked() {
            val preference = boundPreference ?: return
            // Re-read live so an in-place disabled update is respected without a rebind.
            if (isDisabled(preference)) return
            val newValue = !prefs.rawValue(preference)
            prefs.edit().putBoolean(preference.key, newValue).apply()
            refreshPrefRowValue(binding.prefRowValue, prefs.rawValue(preference))
            activity.handleDrawerPreferenceChange(preference, newValue)
            refreshRowsDisabledBy(preference.key)
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
            (preference == PlayerDrawerPreference.MINIMAL_SEEKBAR_WHILE_SEEKING &&
                !activity.playerUiCustomization.seekbarVisible) ||
                preference.disabledWhenOnKey?.let { prefs.getBoolean(it, false) } == true
    }

    private class SpacerHolder(
        private val binding: DrawerRowSpacerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PlayerDrawerRow.Spacer) {
            val density = binding.root.resources.displayMetrics.density
            val targetHeight = (row.heightDp * density).toInt()
            if (binding.root.layoutParams.height != targetHeight) {
                binding.root.layoutParams = binding.root.layoutParams.apply {
                    height = targetHeight
                }
            }
        }
    }

    private companion object {
        const val ROW_MODE_PREFERENCE = 0
        const val ROW_MODE_OPTION = 1
        const val ROW_MODE_NETWORK = 2
    }
}

private fun measureDrawerScrollbarMetrics(
    recyclerView: RecyclerView,
    rows: List<PlayerDrawerRow>,
    rowsHash: Int,
    caches: LinkedHashMap<DrawerScrollbarCacheKey, DrawerScrollbarHeightCache>,
    scratchViews: DrawerMeasureScratchViews,
): StableScrollbarMetrics? {
    val extent = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
    val width = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
    if (rows.isEmpty() || extent <= 0 || width <= 0) {
        return null
    }

    val key = DrawerScrollbarCacheKey(
        width = width,
        rowsHash = rowsHash,
        fontValue = UiFont.currentValue(recyclerView.context),
    )
    val heightCache = caches[key] ?: measureDrawerRowHeights(recyclerView, rows, width, key, scratchViews).also {
        if (caches.size >= MAX_SCROLLBAR_CACHE_ENTRIES) {
            caches.entries.iterator().next().let { oldest -> caches.remove(oldest.key) }
        }
        caches[key] = it
    }
    val range = heightCache.contentHeight + recyclerView.paddingTop + recyclerView.paddingBottom
    val offset = drawerStableScrollOffset(recyclerView, heightCache.cumulativeHeights)
    return StableScrollbarMetrics(range = range, extent = extent, offset = offset)
}

private fun measureDrawerRowHeights(
    parent: RecyclerView,
    rows: List<PlayerDrawerRow>,
    width: Int,
    key: DrawerScrollbarCacheKey,
    scratchViews: DrawerMeasureScratchViews,
): DrawerScrollbarHeightCache {
    val inflater = LayoutInflater.from(parent.context)
    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val networkOverrides = if (rows.any { it is PlayerDrawerRow.Network }) {
        parent.context.networkOverrides()
    } else {
        emptyMap()
    }
    val cumulativeHeights = IntArray(rows.size + 1)
    var totalHeight = 0
    for (index in rows.indices) {
        totalHeight += rows[index].measureDrawerRowHeight(
            parent, inflater, widthSpec, heightSpec, scratchViews, networkOverrides,
        )
        cumulativeHeights[index + 1] = totalHeight
    }
    return DrawerScrollbarHeightCache(
        key = key,
        cumulativeHeights = cumulativeHeights,
        contentHeight = totalHeight,
    )
}

private fun PlayerDrawerRow.measureDrawerRowHeight(
    parent: RecyclerView,
    inflater: LayoutInflater,
    widthSpec: Int,
    heightSpec: Int,
    scratchViews: DrawerMeasureScratchViews,
    networkOverrides: Map<NetworkSetting, String>,
): Int {
    val view = bindMeasureRow(parent, inflater, scratchViews, networkOverrides)
    view.measure(widthSpec, heightSpec)
    val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
    return view.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
}

private fun PlayerDrawerRow.bindMeasureRow(
    parent: RecyclerView,
    inflater: LayoutInflater,
    scratchViews: DrawerMeasureScratchViews,
    networkOverrides: Map<NetworkSetting, String>,
): View = when (this) {
    is PlayerDrawerRow.Button -> bindButtonMeasureRow(parent, inflater, scratchViews, button.textRes)
    is PlayerDrawerRow.ButtonPair -> {
        val binding = scratchViews.buttonPair ?: DrawerRowTwoButtonsBinding.inflate(inflater, parent, false)
            .also { scratchViews.buttonPair = it }
        binding.drawerRowLeftButton.setText(left.textRes)
        binding.drawerRowRightButton.setText(right.textRes)
        binding.root
    }
    PlayerDrawerRow.Stats -> scratchViews.stats?.root
        ?: DrawerRowStatsBinding.inflate(inflater, parent, false).also { scratchViews.stats = it }.root
    is PlayerDrawerRow.Preference,
    is PlayerDrawerRow.Option,
    is PlayerDrawerRow.Network,
    -> bindPrefMeasureRow(parent, inflater, scratchViews, networkOverrides)
    is PlayerDrawerRow.Spacer -> bindSpacerMeasureRow(parent, inflater, scratchViews, heightDp)
}

private fun bindButtonMeasureRow(
    parent: RecyclerView,
    inflater: LayoutInflater,
    scratchViews: DrawerMeasureScratchViews,
    textRes: Int,
): View {
    val binding = scratchViews.button ?: DrawerRowFullButtonBinding.inflate(inflater, parent, false)
        .also { scratchViews.button = it }
    binding.drawerRowButton.setText(textRes)
    return binding.root
}

private fun PlayerDrawerRow.bindPrefMeasureRow(
    parent: RecyclerView,
    inflater: LayoutInflater,
    scratchViews: DrawerMeasureScratchViews,
    networkOverrides: Map<NetworkSetting, String>,
): View {
    val binding = scratchViews.prefRow ?: DrawerPrefRowBinding.inflate(inflater, parent, false)
        .also { scratchViews.prefRow = it }
    when (this) {
        is PlayerDrawerRow.Preference -> {
            binding.prefRowTitle.setText(preference.titleRes)
            binding.prefRowSummary.setText(preference.summaryRes)
            binding.prefRowValue.setText(R.string.status_on)
        }
        is PlayerDrawerRow.Option -> {
            binding.prefRowTitle.setText(option.titleRes)
            binding.prefRowSummary.setText(option.summaryRes)
            binding.prefRowValue.setText(R.string.drawer_measure_decoder_value)
        }
        is PlayerDrawerRow.Network -> {
            binding.prefRowTitle.setText(setting.titleRes)
            binding.prefRowSummary.setText(setting.summaryRes)
            binding.prefRowValue.setTextIfChanged(parent.context.networkValueLabel(setting, networkOverrides))
        }
        else -> Unit
    }
    return binding.root
}

private fun bindSpacerMeasureRow(
    parent: RecyclerView,
    inflater: LayoutInflater,
    scratchViews: DrawerMeasureScratchViews,
    heightDp: Int,
): View {
    val binding = scratchViews.spacer ?: DrawerRowSpacerBinding.inflate(inflater, parent, false)
        .also { scratchViews.spacer = it }
    val density = parent.resources.displayMetrics.density
    val targetHeight = (heightDp * density).toInt()
    if (binding.root.layoutParams.height != targetHeight) {
        binding.root.layoutParams = binding.root.layoutParams.apply { height = targetHeight }
    }
    return binding.root
}

private fun drawerStableScrollOffset(
    recyclerView: RecyclerView,
    cumulativeHeights: IntArray,
): Int {
    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
    val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
    return when {
        layoutManager == null -> recyclerView.computeVerticalScrollOffset()
        firstVisible == RecyclerView.NO_POSITION -> 0
        else -> {
            val firstTop = layoutManager.findViewByPosition(firstVisible)?.top ?: recyclerView.paddingTop
            val previousHeight = cumulativeHeights[firstVisible.coerceIn(0, cumulativeHeights.lastIndex)]
            previousHeight + recyclerView.paddingTop - firstTop
        }
    }
}

private fun SharedPreferences.rawValue(preference: PlayerDrawerPreference): Boolean {
    return when (preference) {
        PlayerDrawerPreference.HI10P_FALLBACK -> hi10pFallbackEnabled()
        PlayerDrawerPreference.MPEG2_SOFTWARE_FALLBACK -> mpeg2SoftwareFallbackEnabled()
        PlayerDrawerPreference.AUTOPAUSE_HI10P -> autoPauseHi10pEnabled()
        else -> getBoolean(preference.key, preference.defaultValue)
    }
}

private fun MPVActivity.applyPreferenceValueLayout(valueView: TextView) {
    val targetWidth = Utils.convertDp(this@applyPreferenceValueLayout, PREF_ROW_VALUE_WIDTH_DP)
    if (valueView.layoutParams.width != targetWidth) {
        valueView.updateLayoutParams<LinearLayout.LayoutParams> { width = targetWidth }
    }
    if (valueView.minWidth != 0) valueView.minWidth = 0
    if (valueView.maxWidth != Int.MAX_VALUE) valueView.maxWidth = Int.MAX_VALUE
    if (valueView.maxLines != 1) valueView.maxLines = 1
    valueView.setHorizontallyScrolling(false)
    if (valueView.ellipsize != null) valueView.ellipsize = null
    val targetGravity = Gravity.CENTER_VERTICAL or Gravity.END
    if (valueView.gravity != targetGravity) valueView.gravity = targetGravity
}

private fun MPVActivity.applyOptionValueLayout(valueView: TextView) {
    val targetWidth = Utils.convertDp(this@applyOptionValueLayout, OPTION_ROW_VALUE_MAX_WIDTH_DP)
    val targetMinWidth = Utils.convertDp(this@applyOptionValueLayout, OPTION_ROW_VALUE_MIN_WIDTH_DP)
    if (valueView.layoutParams.width != targetWidth) {
        valueView.updateLayoutParams<LinearLayout.LayoutParams> { width = targetWidth }
    }
    if (valueView.minWidth != targetMinWidth) valueView.minWidth = targetMinWidth
    if (valueView.maxWidth != targetWidth) valueView.maxWidth = targetWidth
    if (valueView.maxLines != 1) valueView.maxLines = 1
    valueView.setHorizontallyScrolling(true)
    if (valueView.ellipsize != TextUtils.TruncateAt.MARQUEE) {
        valueView.ellipsize = TextUtils.TruncateAt.MARQUEE
    }
    if (valueView.marqueeRepeatLimit != -1) valueView.marqueeRepeatLimit = -1
    val availableTextWidth = targetWidth - valueView.paddingStart - valueView.paddingEnd
    val overflows = valueView.paint.measureText(valueView.text.toString()) > availableTextWidth
    val targetGravity = Gravity.CENTER_VERTICAL or if (overflows) Gravity.START else Gravity.END
    if (valueView.gravity != targetGravity) valueView.gravity = targetGravity
}

private fun refreshPrefRowValue(valueView: TextView, on: Boolean) {
    valueView.setText(if (on) R.string.status_on else R.string.status_off)
    valueView.alpha = if (on) 1f else PREF_ROW_OFF_ALPHA
}
