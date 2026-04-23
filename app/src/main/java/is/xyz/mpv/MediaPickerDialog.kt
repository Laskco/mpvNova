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
/**
 * Unified TV-styled picker dialog for subtitle / audio / decoder selection.
 *
 * The layout (dialog_media_picker.xml) has:
 *   - a title top-left
 *   - a RecyclerView on the left holding the selectable rows
 *   - an optional side panel on the right with:
 *       * a clickable "Delay" tile (for subs)
 *       * sound shaping controls (for audio)
 *
 * Each row uses the same CheckedTextView styling as the old track dialog,
 * so the "selected" item gets the bright pill with the purple check.
 */
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

    private lateinit var binding: DialogMediaPickerBinding

    private var items: List<Item> = emptyList()
    private var voiceBoostState = ValueState("", false)
    private var volumeBoostState = ValueState("", false)
    private var nightModeState = ValueState("", false)
    private var audioNormState = ValueState("", false)
    private var downmixState = ValueState("", false)
    private var persistFiltersEnabled = false

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
    ): View {
        binding = DialogMediaPickerBinding.inflate(layoutInflater)

        binding.pickerTitle.text = title
        binding.pickerSubtitle.text = when {
            showFilters -> binding.root.context.getString(R.string.dialog_picker_subtitle_audio)
            showDelay -> binding.root.context.getString(R.string.dialog_picker_subtitle_subs)
            else -> binding.root.context.getString(R.string.dialog_picker_subtitle_decoder)
        }
        binding.listSectionTitle.text = if (showFilters || showDelay) {
            binding.root.context.getString(R.string.dialog_section_tracks)
        } else {
            binding.root.context.getString(R.string.dialog_section_modes)
        }
        val listMinHeight = Utils.convertDp(binding.root.context, 380f)
        binding.list.layoutParams = binding.list.layoutParams.apply {
            height = if (showFilters) 0 else listMinHeight
            if (this is ViewGroup.LayoutParams) {
                // no-op, keeps type-safe apply block on all devices
            }
            if (this is android.widget.LinearLayout.LayoutParams) {
                weight = if (showFilters) 1f else 0f
            }
        }
        binding.list.minimumHeight = if (showFilters) 0 else listMinHeight
        if (showDelay && !showFilters) {
            binding.listSectionSummary.isVisible = true
            binding.trackPanel.layoutParams = binding.trackPanel.layoutParams.apply {
                height = Utils.convertDp(binding.root.context, 420f)
            }
            binding.sidePanel.layoutParams = binding.sidePanel.layoutParams.apply {
                width = Utils.convertDp(binding.root.context, 460f)
            }
        } else {
            binding.listSectionSummary.isVisible = true
            binding.trackPanel.layoutParams = binding.trackPanel.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        this.items = items

        binding.list.adapter = Adapter(this)

        // Side panel — only visible if either subsection is requested.
        binding.sidePanel.isVisible = showDelay || showFilters
        binding.persistFiltersRow.isVisible = showFilters

        // Delay row (subs).
        binding.delayRow.isVisible = showDelay
        if (showDelay) {
            binding.delayValue.text = delayText ?: "0.00 s"
            binding.delayRow.setOnClickListener { onDelayClick?.invoke() }
        }

        // Filter toggles (audio).
        binding.filterGroup.isVisible = showFilters
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

    private fun syncAdjustButton(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        if (!enabled && button.isFocused) {
            button.clearFocus()
        }
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
