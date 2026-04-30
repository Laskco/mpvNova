package app.mpvnova.player

import app.mpvnova.player.databinding.DialogMediaPickerBinding
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt
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

    fun buildView(
        layoutInflater: LayoutInflater,
        title: String,
        items: List<Item>,
        showDelay: Boolean = false,
        delayText: String? = null,
        showFilters: Boolean = false,
        initialVoiceBoostState: ValueState = ValueState("",
            active = false),
        initialVolumeBoostState: ValueState = ValueState("",
            active = false),
        initialNightModeState: ValueState = ValueState("",
            active = false),
        initialAudioNormState: ValueState = ValueState("",
            active = false),
        initialDownmixState: ValueState = ValueState("",
            active = false),
        persistFiltersOn: Boolean = false,
        showSubFilters: Boolean = false,
        initialSubScaleState: ValueState = ValueState("",
            active = false),
        initialSubPosState: ValueState = ValueState("",
            active = false),
        initialSecondaryPosState: ValueState = ValueState("",
            active = false),
        initialSecondarySubState: ValueState = ValueState("",
            active = false),
        persistSubFiltersOn: Boolean = false,
    ): View {
        binding = DialogMediaPickerBinding.inflate(layoutInflater)

        binding.pickerTitle.text = title
        binding.pickerSubtitle.text = when {
            showFilters -> binding.root.context.getString(R.string.dialog_picker_subtitle_audio)
            showDelay || showSubFilters -> binding.root.context.getString(R.string.dialog_picker_subtitle_subs)
            else -> binding.root.context.getString(R.string.dialog_picker_subtitle_decoder)
        }
        binding.listSectionTitle.text = if (showFilters || showDelay || showSubFilters) {
            binding.root.context.getString(R.string.dialog_section_tracks)
        } else {
            binding.root.context.getString(R.string.dialog_section_modes)
        }
        val hasFilters = showFilters || showSubFilters
        val listMinHeight = Utils.convertDp(binding.root.context, 280f)
        binding.list.layoutParams = binding.list.layoutParams.apply {
            height = if (hasFilters) 0 else listMinHeight
            if (this is android.widget.LinearLayout.LayoutParams) {
                weight = if (hasFilters) 1f else 0f
            }
        }
        binding.list.minimumHeight = if (hasFilters) 0 else listMinHeight
        binding.listSectionSummary.isVisible = !hasFilters && !showDelay
        if (showDelay && !hasFilters) {
            binding.sidePanel.layoutParams = binding.sidePanel.layoutParams.apply {
                width = Utils.convertDp(binding.root.context, 360f)
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
        this.items = items

        binding.list.adapter = Adapter(this)

        binding.sidePanel.isVisible = showDelay || hasFilters
        binding.persistFiltersRow.isVisible = showFilters
        binding.persistSubFiltersRow.isVisible = showSubFilters
        binding.filterScroll.isVisible = hasFilters
        binding.voiceBoostRow.isVisible = showFilters
        binding.volumeBoostRow.isVisible = showFilters
        binding.nightModeRow.isVisible = showFilters
        binding.audioNormRow.isVisible = showFilters
        binding.downmixRow.isVisible = showFilters
        binding.subScaleRow.isVisible = showSubFilters
        binding.subPosRow.isVisible = showSubFilters
        binding.secondaryPosRow.isVisible = showSubFilters
        binding.secondarySubRow.isVisible = showSubFilters
        configureResponsiveSizing(showDelay, showFilters, showSubFilters)

        binding.delayRow.isVisible = showDelay
        if (showDelay) {
            binding.delayValue.text = delayText ?: "0.00 s"
            binding.delayRow.setOnClickListener { onDelayClick?.invoke() }
        }

        binding.filterGroup.isVisible = hasFilters
        if (showFilters) {
            this.voiceBoostState = initialVoiceBoostState
            this.volumeBoostState = initialVolumeBoostState
            this.nightModeState = initialNightModeState
            this.audioNormState = initialAudioNormState
            this.downmixState = initialDownmixState
            persistFiltersEnabled = persistFiltersOn
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

        if (showSubFilters) {
            this.subScaleState = initialSubScaleState
            this.subPosState = initialSubPosState
            this.secondaryPosState = initialSecondaryPosState
            this.secondarySubState = initialSecondarySubState
            persistSubFiltersEnabled = persistSubFiltersOn
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

        // Force focus onto the selected row on open so Android TV does not
        // make the user "wake up" the hover state with extra D-pad presses.
        val selectedIdx = items.indexOfFirst { it.selected }
        if (selectedIdx >= 0) {
            focusListItem(selectedIdx)
        } else if (showDelay) {
            binding.delayRow.post { binding.delayRow.requestFocus() }
        }

        Utils.handleInsetsAsPadding(binding.root)
        return binding.root
    }

    private fun configureResponsiveSizing(showDelay: Boolean, showFilters: Boolean, showSubFilters: Boolean) {
        val context = binding.root.context
        val metrics = context.resources.displayMetrics
        // The header (eyebrow + title + subtitle) now lives inside contentRow,
        // so allocate extra space for it when we pin a fixed height.
        val headerPad = Utils.convertDp(context, 96f)
        val hasFilters = showFilters || showSubFilters
        if (hasFilters) {
            val trackHeight = (metrics.heightPixels * 0.48f).roundToInt()
                .coerceAtMost(Utils.convertDp(context, 520f))
                .coerceAtLeast(Utils.convertDp(context, 360f))
            val sideWidth = if (metrics.widthPixels < Utils.convertDp(context, 1500f)) {
                Utils.convertDp(context, 420f)
            } else {
                Utils.convertDp(context, 430f)
            }

            binding.contentRow.layoutParams = binding.contentRow.layoutParams.apply {
                height = trackHeight + headerPad
            }
            binding.sidePanel.layoutParams = binding.sidePanel.layoutParams.apply {
                width = sideWidth
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            binding.filterScroll.layoutParams = binding.filterScroll.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        } else if (showDelay) {
            binding.contentRow.layoutParams = binding.contentRow.layoutParams.apply {
                height = Utils.convertDp(context, 340f) + headerPad
            }
        }
    }

    private fun focusListItem(position: Int) {
        if (position < 0) return
        (binding.list.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, 0)
        binding.list.post {
            val holder = binding.list.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                binding.list.postDelayed({
                    binding.list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                }, 40L)
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
        binding.voiceBoostValue.text = voiceBoostState.label
        binding.voiceBoostRow.alpha = when {
            !voiceBoostState.enabled -> 0.58f
            voiceBoostState.active -> 1f
            else -> 0.92f
        }
        binding.voiceBoostValue.alpha = when {
            !voiceBoostState.enabled -> 0.55f
            voiceBoostState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.voiceBoostMinusBtn, voiceBoostState.canDecrease)
        syncAdjustButton(binding.voiceBoostPlusBtn, voiceBoostState.canIncrease)
        binding.volumeBoostValue.text = volumeBoostState.label
        binding.volumeBoostRow.alpha = when {
            !volumeBoostState.enabled -> 0.58f
            volumeBoostState.active -> 1f
            else -> 0.92f
        }
        binding.volumeBoostValue.alpha = when {
            !volumeBoostState.enabled -> 0.55f
            volumeBoostState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.volumeBoostMinusBtn, volumeBoostState.canDecrease)
        syncAdjustButton(binding.volumeBoostPlusBtn, volumeBoostState.canIncrease)
        binding.nightModeValue.text = nightModeState.label
        binding.nightModeRow.alpha = when {
            !nightModeState.enabled -> 0.58f
            nightModeState.active -> 1f
            else -> 0.92f
        }
        binding.nightModeValue.alpha = when {
            !nightModeState.enabled -> 0.55f
            nightModeState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.nightModeMinusBtn, nightModeState.canDecrease)
        syncAdjustButton(binding.nightModePlusBtn, nightModeState.canIncrease)
        binding.audioNormValue.text = audioNormState.label
        binding.audioNormRow.alpha = when {
            !audioNormState.enabled -> 0.58f
            audioNormState.active -> 1f
            else -> 0.92f
        }
        binding.audioNormValue.alpha = when {
            !audioNormState.enabled -> 0.55f
            audioNormState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.audioNormMinusBtn, audioNormState.canDecrease)
        syncAdjustButton(binding.audioNormPlusBtn, audioNormState.canIncrease)
        binding.downmixValue.text = downmixState.label
        binding.downmixRow.alpha = when {
            !downmixState.enabled -> 0.58f
            downmixState.active -> 1f
            else -> 0.92f
        }
        binding.downmixValue.alpha = when {
            !downmixState.enabled -> 0.55f
            downmixState.active -> 1f
            else -> 0.72f
        }
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
        binding.subScaleValue.text = subScaleState.label
        binding.subScaleRow.alpha = when {
            !subScaleState.enabled -> 0.58f
            subScaleState.active -> 1f
            else -> 0.92f
        }
        binding.subScaleValue.alpha = when {
            !subScaleState.enabled -> 0.55f
            subScaleState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.subScaleMinusBtn, subScaleState.canDecrease)
        syncAdjustButton(binding.subScalePlusBtn, subScaleState.canIncrease)

        binding.subPosValue.text = subPosState.label
        binding.subPosRow.alpha = when {
            !subPosState.enabled -> 0.58f
            subPosState.active -> 1f
            else -> 0.92f
        }
        binding.subPosValue.alpha = when {
            !subPosState.enabled -> 0.55f
            subPosState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.subPosMinusBtn, subPosState.canDecrease)
        syncAdjustButton(binding.subPosPlusBtn, subPosState.canIncrease)

        binding.secondaryPosValue.text = secondaryPosState.label
        binding.secondaryPosRow.alpha = when {
            !secondaryPosState.enabled -> 0.58f
            secondaryPosState.active -> 1f
            else -> 0.92f
        }
        binding.secondaryPosValue.alpha = when {
            !secondaryPosState.enabled -> 0.55f
            secondaryPosState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.secondaryPosMinusBtn, secondaryPosState.canDecrease)
        syncAdjustButton(binding.secondaryPosPlusBtn, secondaryPosState.canIncrease)

        binding.secondarySubValue.text = secondarySubState.label
        binding.secondarySubRow.alpha = when {
            !secondarySubState.enabled -> 0.58f
            secondarySubState.active -> 1f
            else -> 0.92f
        }
        binding.secondarySubValue.alpha = when {
            !secondarySubState.enabled -> 0.55f
            secondarySubState.active -> 1f
            else -> 0.72f
        }
        syncAdjustButton(binding.secondarySubMinusBtn, secondarySubState.canDecrease)
        syncAdjustButton(binding.secondarySubPlusBtn, secondarySubState.canIncrease)
        syncAdjustButton(binding.secondarySubSwapBtn, secondarySubState.active)

        binding.persistSubFiltersCheck.visibility = if (persistSubFiltersEnabled) View.VISIBLE else View.INVISIBLE
    }

    private fun syncAdjustButton(button: ImageButton, enabled: Boolean) {
        // Stay focusable even when "disabled" — otherwise hammering +/- past
        // the limit causes Android TV to yank focus out to the next panel and
        // the user accidentally fires whatever was next over there. The click
        // becomes a silent no-op via isClickable instead, and the visual dim
        // still tells the user the limit was reached.
        button.isEnabled = true
        button.isClickable = enabled
        button.isFocusable = true
        button.alpha = if (enabled) 1f else 0.38f
        button.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                button.context,
                if (enabled) R.color.tv_text else R.color.tint_disabled
            )
        )
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
