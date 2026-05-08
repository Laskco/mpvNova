package app.mpvnova.player

import app.mpvnova.player.databinding.DialogMediaPickerBinding
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

private const val TRACK_LIST_MIN_HEIGHT_DP = 280f
private const val DELAY_SIDE_PANEL_WIDTH_DP = 360f
private const val HEADER_PADDING_DP = 96f
private const val FILTER_TRACK_HEIGHT_RATIO = 0.48f
private const val FILTER_TRACK_MAX_HEIGHT_DP = 520f
private const val FILTER_TRACK_MIN_HEIGHT_DP = 360f
private const val COMPACT_LAYOUT_WIDTH_DP = 1500f
private const val COMPACT_SIDE_PANEL_WIDTH_DP = 420f
private const val REGULAR_SIDE_PANEL_WIDTH_DP = 430f
private const val DELAY_CONTENT_HEIGHT_DP = 340f
private const val FOCUS_RETRY_DELAY_MS = 40L
private const val DISABLED_ROW_ALPHA = 0.58f
private const val INACTIVE_ROW_ALPHA = 0.92f
private const val DISABLED_VALUE_ALPHA = 0.55f
private const val INACTIVE_VALUE_ALPHA = 0.72f
private const val DISABLED_BUTTON_ALPHA = 0.38f

internal class MediaPickerDialog {

    data class Item(val label: CharSequence, val tag: Any?, val selected: Boolean)
    data class ValueState(
        val label: String,
        val active: Boolean,
        val enabled: Boolean = true,
        val canDecrease: Boolean = true,
        val canIncrease: Boolean = true,
    )
    data class FilterStates(
        val voiceBoost: ValueState,
        val volumeBoost: ValueState,
        val nightMode: ValueState,
        val audioNorm: ValueState,
        val downmix: ValueState,
    )
    data class SubFilterStates(
        val subScale: ValueState,
        val subPos: ValueState,
        val secondaryPos: ValueState,
        val secondarySub: ValueState,
    )
    data class Options(
        val title: String,
        val items: List<Item>,
        val showDelay: Boolean = false,
        val delayText: String? = null,
        val showFilters: Boolean = false,
        val initialVoiceBoostState: ValueState = ValueState("", active = false),
        val initialVolumeBoostState: ValueState = ValueState("", active = false),
        val initialNightModeState: ValueState = ValueState("", active = false),
        val initialAudioNormState: ValueState = ValueState("", active = false),
        val initialDownmixState: ValueState = ValueState("", active = false),
        val persistFiltersOn: Boolean = false,
        val showSubFilters: Boolean = false,
        val initialSubScaleState: ValueState = ValueState("", active = false),
        val initialSubPosState: ValueState = ValueState("", active = false),
        val initialSecondaryPosState: ValueState = ValueState("", active = false),
        val initialSecondarySubState: ValueState = ValueState("", active = false),
        val persistSubFiltersOn: Boolean = false,
    )

    private lateinit var binding: DialogMediaPickerBinding

    /** Current row data backing the list. Readable so callers can translate an
     *  item index back to its tag without holding their own parallel list. */
    var items: List<Item> = emptyList()
        private set
    private var voiceBoostState = ValueState("", false)
    private var volumeBoostState = ValueState("", false)
    private var nightModeState = ValueState("", false)
    private var audioNormState = ValueState("", false)
    private var downmixState = ValueState("", false)
    private var persistFiltersEnabled = false
    private var subScaleState = ValueState("", false)
    private var subPosState = ValueState("", false)
    private var secondaryPosState = ValueState("", false)
    private var secondarySubState = ValueState("", false)
    private var persistSubFiltersEnabled = false

    /** Called when the user clicks a row. Index into [items]. */
    var onItemClick: ((Int) -> Unit)? = null

    /** Called when the user clicks the "Delay" tile (subs only). */
    var onDelayClick: (() -> Unit)? = null

    /** Called when the user toggles the respective filter (audio only). */
    var onVoiceBoostAdjust: ((Int) -> ValueState)? = null
    var onVolumeBoostAdjust: ((Int) -> ValueState)? = null
    var onNightModeAdjust: ((Int) -> ValueState)? = null
    var onAudioNormAdjust: ((Int) -> ValueState)? = null
    var onDownmixAdjust: ((Int) -> ValueState)? = null
    var onPersistClick: (() -> Unit)? = null
    var onFilterStatesRefresh: (() -> FilterStates)? = null

    /** Called when the user adjusts a subtitle filter (subs only). */
    var onSubScaleAdjust: ((Int) -> ValueState)? = null
    var onSubPosAdjust: ((Int) -> ValueState)? = null
    var onSecondaryPosAdjust: ((Int) -> ValueState)? = null
    var onSecondarySubAdjust: ((Int) -> ValueState)? = null
    var onSecondarySubSwap: (() -> Unit)? = null
    var onPersistSubClick: (() -> Unit)? = null
    var onSubFilterStatesRefresh: (() -> SubFilterStates)? = null

    fun buildView(layoutInflater: LayoutInflater, options: Options): View {
        binding = DialogMediaPickerBinding.inflate(layoutInflater)

        binding.bindHeader(options)
        configureList(options)
        binding.configurePanelVisibility(options)
        binding.configureResponsiveSizing(options.showDelay, options.showFilters, options.showSubFilters)
        binding.configureDelay(options, onDelayClick)
        configureAudioFilters(options)
        configureSubtitleFilters(options)
        binding.requestInitialFocus(options)

        handleInsetsAsPadding(binding.root)
        return binding.root
    }

    private fun configureList(options: Options) {
        val hasFilters = options.showFilters || options.showSubFilters
        val listMinHeight = Utils.convertDp(binding.root.context, TRACK_LIST_MIN_HEIGHT_DP)
        binding.list.layoutParams = binding.list.layoutParams.apply {
            height = if (hasFilters) 0 else listMinHeight
            if (this is android.widget.LinearLayout.LayoutParams) {
                weight = if (hasFilters) 1f else 0f
            }
        }
        binding.list.minimumHeight = if (hasFilters) 0 else listMinHeight
        binding.listSectionSummary.isVisible = !hasFilters && !options.showDelay
        if (options.showDelay && !hasFilters) {
            binding.sidePanel.layoutParams = binding.sidePanel.layoutParams.apply {
                width = Utils.convertDp(binding.root.context, DELAY_SIDE_PANEL_WIDTH_DP)
            }
        } else if (!hasFilters) {
            // No side panel: let the track list drive the height so the dialog
            // wraps to content instead of stretching on an unbounded parent.
            binding.trackPanel.layoutParams =
                (binding.trackPanel.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
        }
        this.items = options.items
        binding.list.adapter = Adapter(this)
    }

    private fun configureAudioFilters(options: Options) {
        binding.filterGroup.isVisible = options.showFilters || options.showSubFilters
        if (options.showFilters) {
            this.voiceBoostState = options.initialVoiceBoostState
            this.volumeBoostState = options.initialVolumeBoostState
            this.nightModeState = options.initialNightModeState
            this.audioNormState = options.initialAudioNormState
            this.downmixState = options.initialDownmixState
            persistFiltersEnabled = options.persistFiltersOn
            syncFilterChecks()

            binding.voiceBoostMinusBtn.setOnClickListener {
                voiceBoostState = onVoiceBoostAdjust?.invoke(-1) ?: voiceBoostState
                refreshFilterStates()
            }
            binding.voiceBoostPlusBtn.setOnClickListener {
                voiceBoostState = onVoiceBoostAdjust?.invoke(1) ?: voiceBoostState
                refreshFilterStates()
            }
            binding.volumeBoostMinusBtn.setOnClickListener {
                volumeBoostState = onVolumeBoostAdjust?.invoke(-1) ?: volumeBoostState
                refreshFilterStates()
            }
            binding.volumeBoostPlusBtn.setOnClickListener {
                volumeBoostState = onVolumeBoostAdjust?.invoke(1) ?: volumeBoostState
                refreshFilterStates()
            }
            binding.nightModeMinusBtn.setOnClickListener {
                nightModeState = onNightModeAdjust?.invoke(-1) ?: nightModeState
                refreshFilterStates()
            }
            binding.nightModePlusBtn.setOnClickListener {
                nightModeState = onNightModeAdjust?.invoke(1) ?: nightModeState
                refreshFilterStates()
            }
            binding.audioNormMinusBtn.setOnClickListener {
                audioNormState = onAudioNormAdjust?.invoke(-1) ?: audioNormState
                refreshFilterStates()
            }
            binding.audioNormPlusBtn.setOnClickListener {
                audioNormState = onAudioNormAdjust?.invoke(1) ?: audioNormState
                refreshFilterStates()
            }
            binding.downmixMinusBtn.setOnClickListener {
                downmixState = onDownmixAdjust?.invoke(-1) ?: downmixState
                refreshFilterStates()
            }
            binding.downmixPlusBtn.setOnClickListener {
                downmixState = onDownmixAdjust?.invoke(1) ?: downmixState
                refreshFilterStates()
            }
            binding.persistFiltersRow.setOnClickListener {
                onPersistClick?.invoke()
                persistFiltersEnabled = !persistFiltersEnabled
                syncFilterChecks()
            }
        }
    }

    private fun configureSubtitleFilters(options: Options) {
        if (options.showSubFilters) {
            this.subScaleState = options.initialSubScaleState
            this.subPosState = options.initialSubPosState
            this.secondaryPosState = options.initialSecondaryPosState
            this.secondarySubState = options.initialSecondarySubState
            persistSubFiltersEnabled = options.persistSubFiltersOn
            syncSubFilterChecks()

            binding.subScaleMinusBtn.setOnClickListener {
                subScaleState = onSubScaleAdjust?.invoke(-1) ?: subScaleState
                refreshSubFilterStates()
            }
            binding.subScalePlusBtn.setOnClickListener {
                subScaleState = onSubScaleAdjust?.invoke(1) ?: subScaleState
                refreshSubFilterStates()
            }
            binding.subPosMinusBtn.setOnClickListener {
                subPosState = onSubPosAdjust?.invoke(-1) ?: subPosState
                refreshSubFilterStates()
            }
            binding.subPosPlusBtn.setOnClickListener {
                subPosState = onSubPosAdjust?.invoke(1) ?: subPosState
                refreshSubFilterStates()
            }
            binding.secondaryPosMinusBtn.setOnClickListener {
                secondaryPosState = onSecondaryPosAdjust?.invoke(-1) ?: secondaryPosState
                refreshSubFilterStates()
            }
            binding.secondaryPosPlusBtn.setOnClickListener {
                secondaryPosState = onSecondaryPosAdjust?.invoke(1) ?: secondaryPosState
                refreshSubFilterStates()
            }
            binding.secondarySubMinusBtn.setOnClickListener {
                secondarySubState = onSecondarySubAdjust?.invoke(-1) ?: secondarySubState
                refreshSubFilterStates()
            }
            binding.secondarySubPlusBtn.setOnClickListener {
                secondarySubState = onSecondarySubAdjust?.invoke(1) ?: secondarySubState
                refreshSubFilterStates()
            }
            binding.secondarySubSwapBtn.setOnClickListener {
                onSecondarySubSwap?.invoke()
                refreshSubFilterStates()
            }
            binding.persistSubFiltersRow.setOnClickListener {
                onPersistSubClick?.invoke()
                persistSubFiltersEnabled = !persistSubFiltersEnabled
                syncSubFilterChecks()
            }
        }
    }

    private fun refreshFilterStates() {
        onFilterStatesRefresh?.invoke()?.let { states ->
            voiceBoostState = states.voiceBoost
            volumeBoostState = states.volumeBoost
            nightModeState = states.nightMode
            audioNormState = states.audioNorm
            downmixState = states.downmix
        }
        syncFilterChecks()
    }

    private fun syncFilterChecks() {
        syncValueRow(binding.voiceBoostRow, binding.voiceBoostValue, voiceBoostState)
        syncValueRow(binding.volumeBoostRow, binding.volumeBoostValue, volumeBoostState)
        syncValueRow(binding.nightModeRow, binding.nightModeValue, nightModeState)
        syncValueRow(binding.audioNormRow, binding.audioNormValue, audioNormState)
        syncValueRow(binding.downmixRow, binding.downmixValue, downmixState)
        syncAdjustButton(binding.voiceBoostMinusBtn, voiceBoostState.canDecrease)
        syncAdjustButton(binding.voiceBoostPlusBtn, voiceBoostState.canIncrease)
        syncAdjustButton(binding.volumeBoostMinusBtn, volumeBoostState.canDecrease)
        syncAdjustButton(binding.volumeBoostPlusBtn, volumeBoostState.canIncrease)
        syncAdjustButton(binding.nightModeMinusBtn, nightModeState.canDecrease)
        syncAdjustButton(binding.nightModePlusBtn, nightModeState.canIncrease)
        syncAdjustButton(binding.audioNormMinusBtn, audioNormState.canDecrease)
        syncAdjustButton(binding.audioNormPlusBtn, audioNormState.canIncrease)
        syncAdjustButton(binding.downmixMinusBtn, downmixState.canDecrease)
        syncAdjustButton(binding.downmixPlusBtn, downmixState.canIncrease)
        binding.persistFiltersCheck.visibility = if (persistFiltersEnabled) View.VISIBLE else View.INVISIBLE
    }

    private fun refreshSubFilterStates() {
        onSubFilterStatesRefresh?.invoke()?.let { states ->
            subScaleState = states.subScale
            subPosState = states.subPos
            secondaryPosState = states.secondaryPos
            secondarySubState = states.secondarySub
        }
        syncSubFilterChecks()
    }

    private fun syncSubFilterChecks() {
        syncValueRow(binding.subScaleRow, binding.subScaleValue, subScaleState)
        syncAdjustButton(binding.subScaleMinusBtn, subScaleState.canDecrease)
        syncAdjustButton(binding.subScalePlusBtn, subScaleState.canIncrease)

        syncValueRow(binding.subPosRow, binding.subPosValue, subPosState)
        syncAdjustButton(binding.subPosMinusBtn, subPosState.canDecrease)
        syncAdjustButton(binding.subPosPlusBtn, subPosState.canIncrease)

        syncValueRow(binding.secondaryPosRow, binding.secondaryPosValue, secondaryPosState)
        syncAdjustButton(binding.secondaryPosMinusBtn, secondaryPosState.canDecrease)
        syncAdjustButton(binding.secondaryPosPlusBtn, secondaryPosState.canIncrease)

        syncValueRow(binding.secondarySubRow, binding.secondarySubValue, secondarySubState)
        syncAdjustButton(binding.secondarySubMinusBtn, secondarySubState.canDecrease)
        syncAdjustButton(binding.secondarySubPlusBtn, secondarySubState.canIncrease)
        syncAdjustButton(binding.secondarySubSwapBtn, secondarySubState.active)

        binding.persistSubFiltersCheck.visibility = if (persistSubFiltersEnabled) View.VISIBLE else View.INVISIBLE
    }

    private fun clickItem(position: Int) {
        onItemClick?.invoke(position)
    }

    /** Swap in a new row list and rebuild the RecyclerView. Used when the
     *  caller state changes while the dialog is open (e.g. swapping the
     *  primary / secondary subtitle pair). */
    fun updateItems(newItems: List<Item>) {
        items = newItems
        binding.list.adapter?.notifyDataSetChanged()
    }

    class Adapter(private val parent: MediaPickerDialog) :
        RecyclerView.Adapter<Adapter.VH>() {

        class VH(private val parent: MediaPickerDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: CheckedTextView = ViewCompat.requireViewById(view, android.R.id.text1)
            init {
                view.setOnClickListener { parent.clickItem(bindingAdapterPosition) }
            }
            fun bind(item: Item) {
                textView.text = item.label
                textView.isChecked = item.selected
                textView.setTextColor(
                    ContextCompat.getColor(
                        textView.context,
                        R.color.tv_text
                    )
                )
            }
        }

        override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(vg.context)
                .inflate(R.layout.dialog_track_item, vg, false)
            return VH(parent, view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(parent.items[position])
        }

        override fun getItemCount() = parent.items.size
    }
}

private fun DialogMediaPickerBinding.bindHeader(options: MediaPickerDialog.Options) {
    pickerTitle.text = options.title
    pickerSubtitle.text = when {
        options.showFilters -> root.context.getString(R.string.dialog_picker_subtitle_audio)
        options.showDelay || options.showSubFilters -> {
            root.context.getString(R.string.dialog_picker_subtitle_subs)
        }
        else -> root.context.getString(R.string.dialog_picker_subtitle_decoder)
    }
    listSectionTitle.text = if (options.showFilters || options.showDelay || options.showSubFilters) {
        root.context.getString(R.string.dialog_section_tracks)
    } else {
        root.context.getString(R.string.dialog_section_modes)
    }
}

private fun DialogMediaPickerBinding.configurePanelVisibility(options: MediaPickerDialog.Options) {
    val hasFilters = options.showFilters || options.showSubFilters
    sidePanel.isVisible = options.showDelay || hasFilters
    persistFiltersRow.isVisible = options.showFilters
    persistSubFiltersRow.isVisible = options.showSubFilters
    filterScroll.isVisible = hasFilters
    voiceBoostRow.isVisible = options.showFilters
    volumeBoostRow.isVisible = options.showFilters
    nightModeRow.isVisible = options.showFilters
    audioNormRow.isVisible = options.showFilters
    downmixRow.isVisible = options.showFilters
    subScaleRow.isVisible = options.showSubFilters
    subPosRow.isVisible = options.showSubFilters
    secondaryPosRow.isVisible = options.showSubFilters
    secondarySubRow.isVisible = options.showSubFilters
}

private fun DialogMediaPickerBinding.configureDelay(
    options: MediaPickerDialog.Options,
    onDelayClick: (() -> Unit)?
) {
    delayRow.isVisible = options.showDelay
    if (options.showDelay) {
        delayValue.text = options.delayText ?: "0.00 s"
        delayRow.setOnClickListener { onDelayClick?.invoke() }
    }
}

private fun DialogMediaPickerBinding.configureResponsiveSizing(
    showDelay: Boolean,
    showFilters: Boolean,
    showSubFilters: Boolean
) {
    val context = root.context
    val metrics = context.resources.displayMetrics
    val headerPad = Utils.convertDp(context, HEADER_PADDING_DP)
    if (showFilters || showSubFilters) {
        val trackHeight = (metrics.heightPixels * FILTER_TRACK_HEIGHT_RATIO).roundToInt()
            .coerceAtMost(Utils.convertDp(context, FILTER_TRACK_MAX_HEIGHT_DP))
            .coerceAtLeast(Utils.convertDp(context, FILTER_TRACK_MIN_HEIGHT_DP))
        val sideWidth = if (metrics.widthPixels < Utils.convertDp(context, COMPACT_LAYOUT_WIDTH_DP)) {
            Utils.convertDp(context, COMPACT_SIDE_PANEL_WIDTH_DP)
        } else {
            Utils.convertDp(context, REGULAR_SIDE_PANEL_WIDTH_DP)
        }

        contentRow.layoutParams = contentRow.layoutParams.apply {
            height = trackHeight + headerPad
        }
        sidePanel.layoutParams = sidePanel.layoutParams.apply {
            width = sideWidth
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        filterScroll.layoutParams = filterScroll.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    } else if (showDelay) {
        contentRow.layoutParams = contentRow.layoutParams.apply {
            height = Utils.convertDp(context, DELAY_CONTENT_HEIGHT_DP) + headerPad
        }
    }
}

private fun DialogMediaPickerBinding.requestInitialFocus(options: MediaPickerDialog.Options) {
    val selectedIdx = options.items.indexOfFirst { it.selected }
    if (selectedIdx >= 0) {
        focusListItem(selectedIdx)
    } else if (options.showDelay) {
        delayRow.post { delayRow.requestFocus() }
    }
}

private fun DialogMediaPickerBinding.focusListItem(position: Int) {
    if (position < 0) return
    (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    list.post {
        val holder = list.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            holder.itemView.requestFocus()
        } else {
            list.postDelayed({
                list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }, FOCUS_RETRY_DELAY_MS)
        }
    }
}

private fun syncValueRow(row: View, value: TextView, state: MediaPickerDialog.ValueState) {
    value.text = state.label
    row.alpha = when {
        !state.enabled -> DISABLED_ROW_ALPHA
        state.active -> 1f
        else -> INACTIVE_ROW_ALPHA
    }
    value.alpha = when {
        !state.enabled -> DISABLED_VALUE_ALPHA
        state.active -> 1f
        else -> INACTIVE_VALUE_ALPHA
    }
}

private fun syncAdjustButton(button: ImageButton, enabled: Boolean) {
    // Keep focus stable when a +/- button reaches its limit.
    button.isEnabled = true
    button.isClickable = enabled
    button.isFocusable = true
    button.alpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA
    button.imageTintList = ColorStateList.valueOf(
        ContextCompat.getColor(
            button.context,
            if (enabled) R.color.tv_text else R.color.tint_disabled
        )
    )
}
