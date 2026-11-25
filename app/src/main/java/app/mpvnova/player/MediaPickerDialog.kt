package app.mpvnova.player

import app.mpvnova.player.databinding.DialogMediaPickerAudioFiltersBinding
import app.mpvnova.player.databinding.DialogMediaPickerBinding
import app.mpvnova.player.databinding.DialogMediaPickerSubFiltersBinding
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
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
private const val PICKER_LIST_VIEW_CACHE_SIZE = 10

internal class MediaPickerDialog {

    data class Item(
        val label: CharSequence,
        val tag: Any?,
        val selected: Boolean,
        val enabled: Boolean = true,
    )
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
        val centerBoost: ValueState,
    )
    data class SubFilterStates(
        val subScale: ValueState,
        val subPos: ValueState,
        val secondaryPos: ValueState,
        val subWidth: ValueState,
        val secondarySub: ValueState,
    )
    data class SubPresetState(
        val presetName: String,
        val subStyleStateText: String,
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
        val initialCenterBoostState: ValueState = ValueState("", active = false),
        val persistFiltersOn: Boolean = false,
        val showSubFilters: Boolean = false,
        val initialSubScaleState: ValueState = ValueState("", active = false),
        val initialSubPosState: ValueState = ValueState("", active = false),
        val initialSecondaryPosState: ValueState = ValueState("", active = false),
        val initialSubWidthState: ValueState = ValueState("", active = false),
        val initialSecondarySubState: ValueState = ValueState("", active = false),
        val persistSubFiltersOn: Boolean = false,
        val subStyleStateText: String = "",
        val showSubPresetCycler: Boolean = false,
        val initialSubPresetName: String = "",
        val showOnlineSubtitleSearch: Boolean = false,
        val showOnlineSubtitleDownloads: Boolean = false,
    )

    private lateinit var binding: DialogMediaPickerBinding
    private var audioBinding: DialogMediaPickerAudioFiltersBinding? = null
    private var subBinding: DialogMediaPickerSubFiltersBinding? = null
    private lateinit var enabledButtonTint: ColorStateList
    private lateinit var disabledButtonTint: ColorStateList

    /** Current row data backing the list. Readable so callers can translate an
     *  item index back to its tag without holding their own parallel list. */
    var items: List<Item> = emptyList()
        private set
    val isAttached: Boolean get() = ::binding.isInitialized && binding.root.isAttachedToWindow
    private var voiceBoostState = ValueState("", false)
    private var volumeBoostState = ValueState("", false)
    private var nightModeState = ValueState("", false)
    private var audioNormState = ValueState("", false)
    private var downmixState = ValueState("", false)
    private var centerBoostState = ValueState("", false)
    private var persistFiltersEnabled = false
    private var subScaleState = ValueState("", false)
    private var subPosState = ValueState("", false)
    private var secondaryPosState = ValueState("", false)
    private var subWidthState = ValueState("", false)
    private var secondarySubState = ValueState("", false)
    private var persistSubFiltersEnabled = false
    var displayedDelayValue: CharSequence? = null
        set(value) {
            field = value
            if (::binding.isInitialized && value != null) binding.delayValue.setTextIfChanged(value)
        }
    var focusDelayRow: () -> Unit = {}
        private set
    var focusSubtitleStyleRow: () -> Unit = {}
        private set
    var focusInitialSelection: () -> Unit = {}
        private set

    /** Called when the user clicks a row. Index into [items]. */
    var onItemClick: ((Int) -> Unit)? = null

    /** Called when the user clicks the delay tile. */
    var onDelayClick: (() -> Unit)? = null

    /** Called when the user toggles the respective filter (audio only). */
    var onVoiceBoostAdjust: ((Int) -> ValueState)? = null
    var onVolumeBoostAdjust: ((Int) -> ValueState)? = null
    var onNightModeAdjust: ((Int) -> ValueState)? = null
    var onAudioNormAdjust: ((Int) -> ValueState)? = null
    var onDownmixAdjust: ((Int) -> ValueState)? = null
    var onCenterBoostAdjust: ((Int) -> ValueState)? = null
    var onPersistClick: (() -> Unit)? = null
    var onFilterStatesRefresh: (() -> FilterStates)? = null

    /** Called when the user adjusts a subtitle filter (subs only). */
    var onSubScaleAdjust: ((Int) -> ValueState)? = null
    var onSubPosAdjust: ((Int) -> ValueState)? = null
    var onSecondaryPosAdjust: ((Int) -> ValueState)? = null
    var onSubWidthAdjust: ((Int) -> ValueState)? = null
    var onSecondarySubAdjust: ((Int) -> ValueState)? = null
    var onSecondarySubSwap: (() -> Unit)? = null
    var onPersistSubClick: (() -> Unit)? = null
    var onSubStyleClick: (() -> Unit)? = null
    var onOnlineSubtitleSearch: (() -> Unit)? = null
    var onOnlineSubtitleDownloads: (() -> Unit)? = null
    var onSubPresetAdjust: ((Int) -> SubPresetState)? = null
    var onSubFilterStatesRefresh: (() -> SubFilterStates)? = null

    var downloadedSubtitlesVisible: Boolean = false
        set(value) {
            field = value
            subBinding?.let { sub ->
                sub.onlineSubtitleDownloads.isVisible = value
                sub.onlineSubtitleActions.isVisible = value || sub.onlineSubtitleSearch.isVisible
            }
        }

    fun buildView(layoutInflater: LayoutInflater, options: Options): View {
        val firstBuild = !::binding.isInitialized
        if (firstBuild) {
            binding = DialogMediaPickerBinding.inflate(layoutInflater)
            binding.list.itemAnimator = null
            binding.list.setItemViewCacheSize(PICKER_LIST_VIEW_CACHE_SIZE)
            binding.delayRow.setOnClickListener { onDelayClick?.invoke() }
            enabledButtonTint = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.tv_text))
            disabledButtonTint = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, R.color.tint_disabled),
            )
            handleInsetsAsPadding(binding.root)
        } else {
            binding.root.detachFromParent()
        }

        val audio = if (options.showFilters) ensureAudioBinding() else audioBinding
        val sub = if (options.showSubFilters) ensureSubBinding() else subBinding

        binding.bindHeader(options)
        sub?.let {
            it.onlineSubtitleActions.isVisible = options.showSubFilters &&
                (options.showOnlineSubtitleSearch || options.showOnlineSubtitleDownloads)
            it.onlineSubtitleSearch.isVisible = options.showSubFilters && options.showOnlineSubtitleSearch
            it.onlineSubtitleDownloads.isVisible = options.showSubFilters && options.showOnlineSubtitleDownloads
        }
        configureList(options)
        binding.configurePanelVisibility(options, audio, sub)
        binding.configureResponsiveSizing(options.showDelay, options.showFilters, options.showSubFilters)
        binding.configureDelay(options)
        configureAudioFilters(options, audio)
        configureSubtitleFilters(options, sub)
        binding.positionDelayRow(options, audio, sub)
        focusDelayRow = { binding.delayRow.post { binding.delayRow.requestFocus() } }
        focusSubtitleStyleRow = {
            subBinding?.subStyleRow?.post { subBinding?.subStyleRow?.requestFocus() }
        }
        focusInitialSelection = {
            if (::binding.isInitialized)
                binding.requestInitialFocus(items, options.showDelay)
        }
        binding.root.post { focusInitialSelection() }

        return binding.root
    }

    private fun ensureAudioBinding(): DialogMediaPickerAudioFiltersBinding {
        audioBinding?.let { return it }
        val inflated = DialogMediaPickerAudioFiltersBinding.bind(binding.audioFiltersStub.inflate())
        audioBinding = inflated
        bindAudioFilterAdjusters(inflated)
        inflated.persistFiltersRow.setOnClickListener {
            onPersistClick?.invoke()
            persistFiltersEnabled = !persistFiltersEnabled
            syncFilterChecks()
        }
        return inflated
    }

    private fun ensureSubBinding(): DialogMediaPickerSubFiltersBinding {
        subBinding?.let { return it }
        val inflated = DialogMediaPickerSubFiltersBinding.bind(binding.subFiltersStub.inflate())
        subBinding = inflated
        inflated.onlineSubtitleSearch.setOnClickListener { onOnlineSubtitleSearch?.invoke() }
        inflated.onlineSubtitleDownloads.setOnClickListener { onOnlineSubtitleDownloads?.invoke() }
        inflated.subStyleRow.setOnClickListener { onSubStyleClick?.invoke() }
        inflated.subPresetMinusBtn.setOnClickListener { adjustSubPreset(-1) }
        inflated.subPresetPlusBtn.setOnClickListener { adjustSubPreset(1) }
        val bindStepper: (View, View, () -> ((Int) -> ValueState)?, (ValueState) -> Unit) -> Unit =
            { minus, plus, adjust, update ->
                minus.setOnClickListener {
                    adjust()?.invoke(-1)?.let(update)
                    refreshSubFilterStates()
                }
                plus.setOnClickListener {
                    adjust()?.invoke(1)?.let(update)
                    refreshSubFilterStates()
                }
            }
        bindStepper(inflated.subScaleMinusBtn, inflated.subScalePlusBtn, { onSubScaleAdjust }) { subScaleState = it }
        bindStepper(inflated.subPosMinusBtn, inflated.subPosPlusBtn, { onSubPosAdjust }) { subPosState = it }
        bindStepper(inflated.secondaryPosMinusBtn, inflated.secondaryPosPlusBtn, { onSecondaryPosAdjust }) {
            secondaryPosState = it
        }
        bindStepper(inflated.subWidthMinusBtn, inflated.subWidthPlusBtn, { onSubWidthAdjust }) { subWidthState = it }
        bindStepper(inflated.secondarySubMinusBtn, inflated.secondarySubPlusBtn, { onSecondarySubAdjust }) {
            secondarySubState = it
        }
        inflated.secondarySubSwapBtn.setOnClickListener {
            onSecondarySubSwap?.invoke()
            refreshSubFilterStates()
        }
        inflated.persistSubFiltersRow.setOnClickListener {
            onPersistSubClick?.invoke()
            persistSubFiltersEnabled = !persistSubFiltersEnabled
            syncSubFilterChecks()
        }
        return inflated
    }

    private fun adjustSubPreset(delta: Int) {
        val sub = subBinding ?: return
        onSubPresetAdjust?.invoke(delta)?.let { state ->
            sub.subPresetValue.setTextIfChanged(state.presetName)
            sub.subStyleValue.setTextIfChanged(state.subStyleStateText)
            refreshSubFilterStates()
        }
    }

    private fun configureList(options: Options) {
        val hasFilters = options.showFilters || options.showSubFilters
        binding.list.stopScroll()
        binding.list.clearFocus()
        updateListLayoutParams(options, hasFilters)
        if (binding.list.adapter == null) {
            binding.list.adapter = Adapter(this)
        }
        updateItems(options.items)
    }

    private fun updateListLayoutParams(options: Options, hasFilters: Boolean) {
        val listMinHeight = Utils.convertDp(binding.root.context, TRACK_LIST_MIN_HEIGHT_DP)
        val targetHeight = if (hasFilters) 0 else listMinHeight
        val targetWeight = if (hasFilters) 1f else 0f
        val listLp = binding.list.layoutParams as android.widget.LinearLayout.LayoutParams
        if (listLp.height != targetHeight || listLp.weight != targetWeight) {
            listLp.height = targetHeight
            listLp.weight = targetWeight
            binding.list.layoutParams = listLp
        }
        val targetMinHeight = if (hasFilters) 0 else listMinHeight
        if (binding.list.minimumHeight != targetMinHeight) {
            binding.list.minimumHeight = targetMinHeight
        }
        if (options.showDelay && !hasFilters) {
            val sideWidth = Utils.convertDp(binding.root.context, DELAY_SIDE_PANEL_WIDTH_DP)
            val sideLp = binding.sidePanel.layoutParams
            if (sideLp.width != sideWidth) {
                sideLp.width = sideWidth
                binding.sidePanel.layoutParams = sideLp
            }
        } else if (!hasFilters) {
            // No side panel: let the track list drive the height so the dialog
            // wraps to content instead of stretching on an unbounded parent.
            val trackLp = binding.trackPanel.layoutParams as android.widget.LinearLayout.LayoutParams
            if (trackLp.height != ViewGroup.LayoutParams.WRAP_CONTENT || trackLp.weight != 0f) {
                trackLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                trackLp.weight = 0f
                binding.trackPanel.layoutParams = trackLp
            }
        }
    }

    private fun configureAudioFilters(options: Options, audio: DialogMediaPickerAudioFiltersBinding?) {
        binding.filterGroup.isVisible = options.showFilters || options.showSubFilters || options.showDelay
        if (options.showFilters && audio != null) {
            bindAudioFilterStates(options)
        }
    }

    private fun bindAudioFilterStates(options: Options) {
        voiceBoostState = options.initialVoiceBoostState
        volumeBoostState = options.initialVolumeBoostState
        nightModeState = options.initialNightModeState
        audioNormState = options.initialAudioNormState
        downmixState = options.initialDownmixState
        centerBoostState = options.initialCenterBoostState
        persistFiltersEnabled = options.persistFiltersOn
        syncFilterChecks()
    }

    private fun bindAudioFilterAdjusters(audio: DialogMediaPickerAudioFiltersBinding) {
        audio.voiceBoostMinusBtn.setOnClickListener {
            voiceBoostState = onVoiceBoostAdjust?.invoke(-1) ?: voiceBoostState
            refreshFilterStates()
        }
        audio.voiceBoostPlusBtn.setOnClickListener {
            voiceBoostState = onVoiceBoostAdjust?.invoke(1) ?: voiceBoostState
            refreshFilterStates()
        }
        audio.volumeBoostMinusBtn.setOnClickListener {
            volumeBoostState = onVolumeBoostAdjust?.invoke(-1) ?: volumeBoostState
            refreshFilterStates()
        }
        audio.volumeBoostPlusBtn.setOnClickListener {
            volumeBoostState = onVolumeBoostAdjust?.invoke(1) ?: volumeBoostState
            refreshFilterStates()
        }
        audio.nightModeMinusBtn.setOnClickListener {
            nightModeState = onNightModeAdjust?.invoke(-1) ?: nightModeState
            refreshFilterStates()
        }
        audio.nightModePlusBtn.setOnClickListener {
            nightModeState = onNightModeAdjust?.invoke(1) ?: nightModeState
            refreshFilterStates()
        }
        audio.audioNormMinusBtn.setOnClickListener {
            audioNormState = onAudioNormAdjust?.invoke(-1) ?: audioNormState
            refreshFilterStates()
        }
        audio.audioNormPlusBtn.setOnClickListener {
            audioNormState = onAudioNormAdjust?.invoke(1) ?: audioNormState
            refreshFilterStates()
        }
        audio.downmixMinusBtn.setOnClickListener {
            downmixState = onDownmixAdjust?.invoke(-1) ?: downmixState
            refreshFilterStates()
        }
        audio.downmixPlusBtn.setOnClickListener {
            downmixState = onDownmixAdjust?.invoke(1) ?: downmixState
            refreshFilterStates()
        }
        audio.centerBoostMinusBtn.setOnClickListener {
            centerBoostState = onCenterBoostAdjust?.invoke(-1) ?: centerBoostState
            refreshFilterStates()
        }
        audio.centerBoostPlusBtn.setOnClickListener {
            centerBoostState = onCenterBoostAdjust?.invoke(1) ?: centerBoostState
            refreshFilterStates()
        }
    }

    private fun configureSubtitleFilters(options: Options, sub: DialogMediaPickerSubFiltersBinding?) {
        if (!options.showSubFilters || sub == null) return
        subScaleState = options.initialSubScaleState
        subPosState = options.initialSubPosState
        secondaryPosState = options.initialSecondaryPosState
        subWidthState = options.initialSubWidthState
        secondarySubState = options.initialSecondarySubState
        persistSubFiltersEnabled = options.persistSubFiltersOn
        sub.subStyleValue.setTextIfChanged(options.subStyleStateText)
        sub.subPresetValue.setTextIfChanged(options.initialSubPresetName)
        syncSubFilterChecks()
    }

    private fun refreshFilterStates() {
        onFilterStatesRefresh?.invoke()?.let { states ->
            voiceBoostState = states.voiceBoost
            volumeBoostState = states.volumeBoost
            nightModeState = states.nightMode
            audioNormState = states.audioNorm
            downmixState = states.downmix
            centerBoostState = states.centerBoost
        }
        syncFilterChecks()
    }

    private fun syncFilterChecks() {
        val audio = audioBinding ?: return
        syncValueRow(audio.voiceBoostRow, audio.voiceBoostValue, voiceBoostState)
        syncValueRow(audio.volumeBoostRow, audio.volumeBoostValue, volumeBoostState)
        syncValueRow(audio.nightModeRow, audio.nightModeValue, nightModeState)
        syncValueRow(audio.audioNormRow, audio.audioNormValue, audioNormState)
        syncValueRow(audio.downmixRow, audio.downmixValue, downmixState)
        syncValueRow(audio.centerBoostRow, audio.centerBoostValue, centerBoostState)
        val onTint = enabledButtonTint
        val offTint = disabledButtonTint
        syncAdjustButton(audio.voiceBoostMinusBtn, voiceBoostState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.voiceBoostPlusBtn, voiceBoostState.canIncrease, onTint, offTint)
        syncAdjustButton(audio.volumeBoostMinusBtn, volumeBoostState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.volumeBoostPlusBtn, volumeBoostState.canIncrease, onTint, offTint)
        syncAdjustButton(audio.nightModeMinusBtn, nightModeState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.nightModePlusBtn, nightModeState.canIncrease, onTint, offTint)
        syncAdjustButton(audio.audioNormMinusBtn, audioNormState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.audioNormPlusBtn, audioNormState.canIncrease, onTint, offTint)
        syncAdjustButton(audio.downmixMinusBtn, downmixState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.downmixPlusBtn, downmixState.canIncrease, onTint, offTint)
        syncAdjustButton(audio.centerBoostMinusBtn, centerBoostState.canDecrease, onTint, offTint)
        syncAdjustButton(audio.centerBoostPlusBtn, centerBoostState.canIncrease, onTint, offTint)
        val persistVis = if (persistFiltersEnabled) View.VISIBLE else View.INVISIBLE
        audio.persistFiltersCheck.setVisibilityIfChanged(persistVis)
    }

    private fun refreshSubFilterStates() {
        onSubFilterStatesRefresh?.invoke()?.let { states ->
            subScaleState = states.subScale
            subPosState = states.subPos
            secondaryPosState = states.secondaryPos
            subWidthState = states.subWidth
            secondarySubState = states.secondarySub
        }
        syncSubFilterChecks()
    }

    private fun syncSubFilterChecks() {
        val sub = subBinding ?: return
        val onTint = enabledButtonTint
        val offTint = disabledButtonTint
        syncValueRow(sub.subScaleRow, sub.subScaleValue, subScaleState)
        syncAdjustButton(sub.subScaleMinusBtn, subScaleState.canDecrease, onTint, offTint)
        syncAdjustButton(sub.subScalePlusBtn, subScaleState.canIncrease, onTint, offTint)

        syncValueRow(sub.subPosRow, sub.subPosValue, subPosState)
        syncAdjustButton(sub.subPosMinusBtn, subPosState.canDecrease, onTint, offTint)
        syncAdjustButton(sub.subPosPlusBtn, subPosState.canIncrease, onTint, offTint)

        syncValueRow(sub.secondaryPosRow, sub.secondaryPosValue, secondaryPosState)
        syncAdjustButton(sub.secondaryPosMinusBtn, secondaryPosState.canDecrease, onTint, offTint)
        syncAdjustButton(sub.secondaryPosPlusBtn, secondaryPosState.canIncrease, onTint, offTint)

        syncValueRow(sub.subWidthRow, sub.subWidthValue, subWidthState)
        syncAdjustButton(sub.subWidthMinusBtn, subWidthState.canDecrease, onTint, offTint)
        syncAdjustButton(sub.subWidthPlusBtn, subWidthState.canIncrease, onTint, offTint)

        syncValueRow(sub.secondarySubRow, sub.secondarySubValue, secondarySubState)
        syncAdjustButton(sub.secondarySubMinusBtn, secondarySubState.canDecrease, onTint, offTint)
        syncAdjustButton(sub.secondarySubPlusBtn, secondarySubState.canIncrease, onTint, offTint)
        syncAdjustButton(sub.secondarySubSwapBtn, secondarySubState.active, onTint, offTint)

        val persistSubVis = if (persistSubFiltersEnabled) View.VISIBLE else View.INVISIBLE
        sub.persistSubFiltersCheck.setVisibilityIfChanged(persistSubVis)
    }

    /** Swap in a new row list and rebuild the RecyclerView. Used when the
     *  caller state changes while the dialog is open (e.g. swapping the
     *  primary / secondary subtitle pair). */
    fun updateItems(newItems: List<Item>) {
        val oldCount = items.size
        items = newItems
        binding.list.adapter?.notifyRangeRefresh(oldCount, items.size)
    }

    class Adapter(private val parent: MediaPickerDialog) :
        RecyclerView.Adapter<Adapter.VH>() {

        class VH(private val parent: MediaPickerDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: CheckedTextView = ViewCompat.requireViewById(view, android.R.id.text1)
            init {
                textView.setTextColor(ContextCompat.getColor(textView.context, R.color.tv_text))
                view.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && parent.items[position].enabled)
                        parent.onItemClick?.invoke(position)
                }
            }
            fun bind(item: Item) {
                textView.setTextIfChanged(item.label)
                if (textView.isChecked != item.selected) textView.isChecked = item.selected
                if (itemView.isEnabled != item.enabled) itemView.isEnabled = item.enabled
                if (itemView.isFocusable != item.enabled) itemView.isFocusable = item.enabled
                val targetAlpha = if (item.enabled) 1f else DISABLED_ROW_ALPHA
                if (itemView.alpha != targetAlpha) itemView.alpha = targetAlpha
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
    pickerTitle.setTextIfChanged(options.title)
    val subtitleRes = when {
        options.showFilters -> R.string.dialog_picker_subtitle_audio
        options.showDelay || options.showSubFilters -> R.string.dialog_picker_subtitle_subs
        else -> R.string.dialog_picker_subtitle_decoder
    }
    pickerSubtitle.setTextIfChanged(root.context.getString(subtitleRes))
    val sectionRes = if (options.showFilters || options.showDelay || options.showSubFilters) {
        R.string.dialog_section_tracks
    } else {
        R.string.dialog_section_modes
    }
    listSectionTitle.setTextIfChanged(root.context.getString(sectionRes))
}

private fun DialogMediaPickerBinding.configurePanelVisibility(
    options: MediaPickerDialog.Options,
    audio: DialogMediaPickerAudioFiltersBinding?,
    sub: DialogMediaPickerSubFiltersBinding?,
) {
    val hasFilters = options.showFilters || options.showSubFilters
    sidePanel.isVisible = options.showDelay || hasFilters
    filterScroll.isVisible = hasFilters || options.showDelay
    audio?.let {
        it.root.isVisible = options.showFilters
        it.persistFiltersRow.isVisible = options.showFilters
        it.voiceBoostRow.isVisible = options.showFilters
        it.volumeBoostRow.isVisible = options.showFilters
        it.nightModeRow.isVisible = options.showFilters
        it.audioNormRow.isVisible = options.showFilters
        it.downmixRow.isVisible = options.showFilters
        it.centerBoostRow.isVisible = options.showFilters
    }
    sub?.let {
        it.root.isVisible = options.showSubFilters
        it.persistSubFiltersRow.isVisible = options.showSubFilters
        it.subStyleRow.isVisible = options.showSubFilters
        it.subPresetRow.isVisible = options.showSubFilters && options.showSubPresetCycler
        it.subScaleRow.isVisible = options.showSubFilters
        it.subPosRow.isVisible = options.showSubFilters
        it.secondaryPosRow.isVisible = options.showSubFilters
        it.subWidthRow.isVisible = options.showSubFilters
        it.secondarySubRow.isVisible = options.showSubFilters
    }
}

private fun DialogMediaPickerBinding.configureDelay(options: MediaPickerDialog.Options) {
    delayRow.isVisible = options.showDelay
    if (options.showDelay) {
        delayTitle.setText(if (options.showFilters) R.string.audio_delay else R.string.sub_delay)
        delaySummary.setText(
            if (options.showFilters) R.string.dialog_audio_timing_summary else R.string.dialog_section_timing_summary
        )
        delayValue.setTextIfChanged(options.delayText ?: "0.00 s")
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

        val targetContentHeight = trackHeight + headerPad
        if (contentRow.layoutParams.height != targetContentHeight) {
            contentRow.layoutParams = contentRow.layoutParams.apply { height = targetContentHeight }
        }
        val sideLp = sidePanel.layoutParams
        if (sideLp.width != sideWidth || sideLp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            sideLp.width = sideWidth
            sideLp.height = ViewGroup.LayoutParams.MATCH_PARENT
            sidePanel.layoutParams = sideLp
        }
        if (filterScroll.layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            filterScroll.layoutParams = filterScroll.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    } else if (showDelay) {
        val targetContentHeight = Utils.convertDp(context, DELAY_CONTENT_HEIGHT_DP) + headerPad
        if (contentRow.layoutParams.height != targetContentHeight) {
            contentRow.layoutParams = contentRow.layoutParams.apply { height = targetContentHeight }
        }
    }
}

private fun DialogMediaPickerBinding.requestInitialFocus(items: List<MediaPickerDialog.Item>, showDelay: Boolean) {
    val selectedIdx = items.indexOfFirst { it.selected }
    if (selectedIdx >= 0) {
        focusListItem(selectedIdx)
    } else if (showDelay) {
        delayRow.post { delayRow.requestFocus() }
    }
}

private fun DialogMediaPickerBinding.focusListItem(position: Int) {
    if (position < 0) return
    list.stopScroll()
    list.clearFocus()
    if (list.layoutManager == null) {
        return
    }
    if (!list.isAttachedToWindow || !list.isLaidOut) {
        focusAfterNextLayout(position)
    } else {
        focusLaidOutListItem(position)
    }
}

private fun DialogMediaPickerBinding.focusAfterNextLayout(position: Int) {
    list.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            list.removeOnLayoutChangeListener(this)
            focusListItem(position)
        }
    })
}

private fun DialogMediaPickerBinding.focusLaidOutListItem(position: Int) {
    list.scrollToPosition(position)
    list.post {
        val holder = list.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            holder.itemView.apply {
                requestFocus()
                parent?.requestChildFocus(this, this)
                requestRectangleOnScreen(Rect(0, 0, width, height), true)
            }
        } else {
            list.postDelayed({
                list.scrollToPosition(position)
                list.post {
                    list.findViewHolderForAdapterPosition(position)?.itemView?.apply {
                        requestFocus()
                        parent?.requestChildFocus(this, this)
                        requestRectangleOnScreen(Rect(0, 0, width, height), true)
                    }
                }
            }, FOCUS_RETRY_DELAY_MS)
        }
    }
}

private fun syncValueRow(row: View, value: TextView, state: MediaPickerDialog.ValueState) {
    value.setTextIfChanged(state.label)
    val rowAlpha = when {
        !state.enabled -> DISABLED_ROW_ALPHA
        state.active -> 1f
        else -> INACTIVE_ROW_ALPHA
    }
    if (row.alpha != rowAlpha) row.alpha = rowAlpha
    val valueAlpha = when {
        !state.enabled -> DISABLED_VALUE_ALPHA
        state.active -> 1f
        else -> INACTIVE_VALUE_ALPHA
    }
    if (value.alpha != valueAlpha) value.alpha = valueAlpha
}

private fun syncAdjustButton(
    button: ImageButton,
    enabled: Boolean,
    enabledTint: ColorStateList,
    disabledTint: ColorStateList,
) {
    // Keep focus stable when a +/- button reaches its limit.
    if (!button.isEnabled) button.isEnabled = true
    if (button.isClickable != enabled) button.isClickable = enabled
    if (!button.isFocusable) button.isFocusable = true
    val targetAlpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA
    if (button.alpha != targetAlpha) button.alpha = targetAlpha
    val targetTint = if (enabled) enabledTint else disabledTint
    if (button.imageTintList !== targetTint) button.imageTintList = targetTint
}
