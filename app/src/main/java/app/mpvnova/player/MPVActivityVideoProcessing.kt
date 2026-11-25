package app.mpvnova.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import app.mpvnova.player.databinding.DialogSettingOptionItemBinding
import app.mpvnova.player.databinding.DialogSettingToggleItemBinding
import app.mpvnova.player.databinding.DialogVideoProcessingBinding

private const val VIDEO_PROCESSING_ROW_TITLE_LINES = 2
private const val VIDEO_PROCESSING_ROW_DETAIL_LINES = 3
private const val VIDEO_PROCESSING_LIST_VIEW_CACHE_SIZE = 10
private const val VIEW_TYPE_CHOICE = 0
private const val VIEW_TYPE_TOGGLE = 1

private val TEMPORAL_FILTER_DETAIL_RES = mapOf(
    "oversample" to R.string.video_filter_temporal_oversample_detail,
    "linear" to R.string.video_filter_temporal_linear_detail,
    "catmull_rom" to R.string.video_filter_temporal_catmull_rom_detail,
    "mitchell" to R.string.video_filter_temporal_mitchell_detail,
    "gaussian" to R.string.video_filter_temporal_gaussian_detail,
    "bicubic" to R.string.video_filter_temporal_bicubic_detail,
)

private val SPATIAL_FILTER_DETAIL_RES = mapOf(
    "bilinear" to R.string.video_filter_bilinear_detail,
    "bicubic_fast" to R.string.video_filter_bicubic_fast_detail,
    "oversample" to R.string.video_filter_oversample_detail,
    "spline16" to R.string.video_filter_spline16_detail,
    "spline36" to R.string.video_filter_spline36_detail,
    "spline64" to R.string.video_filter_spline64_detail,
    "sinc" to R.string.video_filter_sinc_detail,
    "lanczos" to R.string.video_filter_lanczos_detail,
    "ginseng" to R.string.video_filter_ginseng_detail,
    "jinc" to R.string.video_filter_jinc_detail,
    "ewa_lanczos" to R.string.video_filter_ewa_lanczos_detail,
    "ewa_hanning" to R.string.video_filter_ewa_hanning_detail,
    "ewa_ginseng" to R.string.video_filter_ewa_ginseng_detail,
    "ewa_lanczossharp" to R.string.video_filter_ewa_lanczossharp_detail,
    "ewa_lanczos4sharpest" to R.string.video_filter_ewa_lanczos4sharpest_detail,
    "ewa_lanczossoft" to R.string.video_filter_ewa_lanczossoft_detail,
    "haasnsoft" to R.string.video_filter_haasnsoft_detail,
    "bicubic" to R.string.video_filter_bicubic_detail,
    "hermite" to R.string.video_filter_hermite_detail,
    "catmull_rom" to R.string.video_filter_catmull_rom_detail,
    "mitchell" to R.string.video_filter_mitchell_detail,
    "robidoux" to R.string.video_filter_robidoux_detail,
    "robidouxsharp" to R.string.video_filter_robidouxsharp_detail,
    "ewa_robidoux" to R.string.video_filter_ewa_robidoux_detail,
    "ewa_robidouxsharp" to R.string.video_filter_ewa_robidouxsharp_detail,
    "box" to R.string.video_filter_box_detail,
    "nearest" to R.string.video_filter_nearest_detail,
    "triangle" to R.string.video_filter_triangle_detail,
    "gaussian" to R.string.video_filter_gaussian_detail,
    "bartlett" to R.string.video_filter_bartlett_detail,
    "cosine" to R.string.video_filter_cosine_detail,
    "tukey" to R.string.video_filter_tukey_detail,
    "hamming" to R.string.video_filter_hamming_detail,
    "quadric" to R.string.video_filter_quadric_detail,
    "welch" to R.string.video_filter_welch_detail,
    "kaiser" to R.string.video_filter_kaiser_detail,
    "blackman" to R.string.video_filter_blackman_detail,
    "sphinx" to R.string.video_filter_sphinx_detail,
)

internal data class PlayerPanelChoice(
    val value: String,
    val title: String,
    val detail: String = "",
    val swatchColor: Int? = null,
)

internal data class PlayerPanelToggle(
    val key: String,
    val title: String,
    val detail: String,
    val checked: Boolean,
)

internal fun MPVActivity.pickVideoScaler(setting: VideoScalerSetting) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val selected = prefs.getString(setting.preferenceKey, VIDEO_PROCESSING_DEFAULT_VALUE)
        .orEmpty()
    val choices = buildList {
        add(
            PlayerPanelChoice(
                VIDEO_PROCESSING_DEFAULT_VALUE,
                getString(R.string.video_processing_default),
                getString(R.string.video_processing_default_detail),
            )
        )
        resources.getStringArray(setting.entriesRes).forEach { value ->
            add(
                PlayerPanelChoice(
                    value,
                    value.videoFilterDisplayName(),
                    getString(value.videoFilterDetailRes(setting)),
                )
            )
        }
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
        titleRes = setting.titleRes,
        summaryRes = setting.summaryRes,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoScaler(setting, value) }
}

internal fun MPVActivity.pickVideoDebanding() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val selected = prefs.getString("video_debanding", VIDEO_PROCESSING_DEFAULT_VALUE).orEmpty()
    val choices = listOf(
        PlayerPanelChoice(
            VIDEO_PROCESSING_DEFAULT_VALUE,
            getString(R.string.video_processing_disabled),
            getString(R.string.video_processing_deband_disabled_detail),
        ),
        PlayerPanelChoice(
            "gradfun",
            getString(R.string.video_processing_deband_cpu),
            getString(R.string.video_processing_deband_cpu_detail),
        ),
        PlayerPanelChoice(
            "gpu",
            getString(R.string.video_processing_deband_gpu),
            getString(R.string.video_processing_deband_gpu_detail),
        ),
    )
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
        titleRes = R.string.pref_video_debanding_title,
        summaryRes = R.string.pref_video_debanding_summary,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoDebanding(value) }
}

internal fun MPVActivity.pickVideoInterpolation() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val enabled = prefs.getBoolean("video_interpolation", false)
    val sync = prefs.getString("video_sync", getString(R.string.pref_video_interpolation_sync_default))
        ?: getString(R.string.pref_video_interpolation_sync_default)
    val selected = if (enabled && sync.startsWith("display-")) sync else VIDEO_PROCESSING_OFF_VALUE
    val choices = buildList {
        add(
            PlayerPanelChoice(
                VIDEO_PROCESSING_OFF_VALUE,
                getString(R.string.video_processing_disabled),
                getString(R.string.video_processing_interpolation_disabled_detail),
            )
        )
        resources.getStringArray(R.array.video_sync)
            .filter { it.startsWith("display-") }
            .forEach { mode ->
                add(
                    PlayerPanelChoice(
                        mode,
                        mode.videoFilterDisplayName(),
                        getString(mode.interpolationModeDetailRes()),
                    )
                )
            }
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_processing,
        titleRes = R.string.pref_video_interpolation_title,
        summaryRes = R.string.pref_video_interpolation_message,
        choices = choices,
        selectedValue = selected,
    ) { value -> setVideoInterpolation(value) }
}

internal fun MPVActivity.openPlayerChoicePanel(
    @StringRes eyebrowRes: Int,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    choices: List<PlayerPanelChoice>,
    selectedValue: String,
    chrome: PlayerDialogChrome = PlayerDialogChrome.HIDE_ALL,
    dismissOnSelection: Boolean = false,
    toggles: List<PlayerPanelToggle> = emptyList(),
    refreshThemeOnChange: Boolean = false,
    refreshFontOnChange: Boolean = false,
    onToggled: (String, Boolean) -> Unit = { _, _ -> },
    onSelected: (String) -> Unit,
) {
    val restore = keepPlaybackForDialog()
    val binding = DialogVideoProcessingBinding.inflate(layoutInflater)
    lateinit var dialog: AlertDialog
    binding.videoProcessingEyebrow.setText(eyebrowRes)
    binding.videoProcessingTitle.setText(titleRes)
    binding.videoProcessingSummary.setText(summaryRes)
    binding.videoProcessingScroll.visibility = View.GONE
    binding.videoProcessingList.visibility = View.VISIBLE

    val rows = buildChoicePanelRows(choices, toggles)
    val toggleStates = BooleanArray(toggles.size) { toggles[it].checked }
    val adapter = PlayerChoicePanelAdapter(
        activity = this,
        binding = binding,
        rows = rows,
        initialSelected = selectedValue,
        toggleStates = toggleStates,
        refreshThemeOnChange = refreshThemeOnChange,
        refreshFontOnChange = refreshFontOnChange,
        onSelected = { value ->
            onSelected(value)
            if (dismissOnSelection) dialog.dismiss()
        },
        onToggled = onToggled,
    )
    configureChoicePanelRecyclerView(binding, adapter)

    dialog = AlertDialog.Builder(this).setView(binding.root).create()
    dialog.setOnDismissListener {
        restore()
        reopenDrawerIfPending()
    }
    binding.videoProcessingDoneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(dialog, VIDEO_PROCESSING_DIALOG_LAYOUT, chrome)
    focusInitialChoiceRow(binding.videoProcessingList, choices, selectedValue, rows.size)
}

private fun buildChoicePanelRows(
    choices: List<PlayerPanelChoice>,
    toggles: List<PlayerPanelToggle>,
): List<ChoicePanelRow> = buildList(choices.size + toggles.size) {
    choices.forEach { add(ChoicePanelRow.Choice(it)) }
    toggles.forEachIndexed { index, toggle -> add(ChoicePanelRow.Toggle(toggle, index)) }
}

private fun configureChoicePanelRecyclerView(
    binding: DialogVideoProcessingBinding,
    adapter: PlayerChoicePanelAdapter,
) {
    binding.videoProcessingList.apply {
        itemAnimator = null
        setHasFixedSize(true)
        setItemViewCacheSize(VIDEO_PROCESSING_LIST_VIEW_CACHE_SIZE)
        this.adapter = adapter
    }
    TvScrollbars.bind(binding.videoProcessingList, binding.videoProcessingScrollbarThumb)
}

private fun focusInitialChoiceRow(
    list: RecyclerView,
    choices: List<PlayerPanelChoice>,
    selectedValue: String,
    rowCount: Int,
) {
    val selectedIndex = choices.indexOfFirst { it.value == selectedValue }
    val focusPosition = when {
        selectedIndex >= 0 -> selectedIndex
        rowCount > 0 -> 0
        else -> return
    }
    list.scrollToPosition(focusPosition)
    list.post {
        list.findViewHolderForAdapterPosition(focusPosition)?.itemView?.requestFocus()
    }
}

private sealed interface ChoicePanelRow {
    data class Choice(val choice: PlayerPanelChoice) : ChoicePanelRow
    data class Toggle(val toggle: PlayerPanelToggle, val toggleIndex: Int) : ChoicePanelRow
}

private class PlayerChoicePanelAdapter(
    private val activity: MPVActivity,
    private val binding: DialogVideoProcessingBinding,
    private val rows: List<ChoicePanelRow>,
    initialSelected: String,
    private val toggleStates: BooleanArray,
    private val refreshThemeOnChange: Boolean,
    private val refreshFontOnChange: Boolean,
    private val onSelected: (String) -> Unit,
    private val onToggled: (String, Boolean) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var currentSelected = initialSelected
        private set
    var themeRevision = 0
        private set

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ChoicePanelRow.Choice -> VIEW_TYPE_CHOICE
        is ChoicePanelRow.Toggle -> VIEW_TYPE_TOGGLE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CHOICE) {
            ChoiceViewHolder(DialogSettingOptionItemBinding.inflate(inflater, parent, false))
        } else {
            ToggleViewHolder(DialogSettingToggleItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ChoicePanelRow.Choice -> (holder as ChoiceViewHolder).bind(row.choice)
            is ChoicePanelRow.Toggle -> (holder as ToggleViewHolder).bind(row)
        }
    }

    private fun handleChoiceClick(position: Int) {
        val row = rows.getOrNull(position) as? ChoicePanelRow.Choice ?: return
        val value = row.choice.value
        val previousIndex = rows.indexOfFirst {
            it is ChoicePanelRow.Choice && it.choice.value == currentSelected
        }
        currentSelected = value
        if (previousIndex >= 0 && previousIndex != position) notifyItemChanged(previousIndex)
        if (previousIndex != position) notifyItemChanged(position)
        onSelected(value)
        applyDynamicRefreshes()
    }

    private fun handleToggleClick(position: Int, toggleView: SwitchCompat) {
        val row = rows.getOrNull(position) as? ChoicePanelRow.Toggle ?: return
        val checked = !toggleStates[row.toggleIndex]
        toggleStates[row.toggleIndex] = checked
        if (toggleView.isChecked != checked) toggleView.isChecked = checked
        onToggled(row.toggle.key, checked)
        applyDynamicRefreshes()
    }

    private fun applyDynamicRefreshes() {
        if (refreshThemeOnChange) {
            themeRevision++
            activity.refreshPlayerChoicePanelTheme(binding)
        }
        if (refreshFontOnChange) UiFont.applyToViewTree(binding.root)
        if (refreshThemeOnChange || refreshFontOnChange) {
            binding.videoProcessingList.setItemViewCacheSize(0)
            binding.videoProcessingList.setItemViewCacheSize(VIDEO_PROCESSING_LIST_VIEW_CACHE_SIZE)
            binding.videoProcessingList.recycledViewPool.clear()
        }
    }

    private inner class ChoiceViewHolder(
        private val itemBinding: DialogSettingOptionItemBinding,
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        private var swatchDrawable: GradientDrawable? = null
        private var boundThemeRevision = 0

        init {
            itemBinding.optionTitleText.maxLines = VIDEO_PROCESSING_ROW_TITLE_LINES
            itemBinding.optionDetailText.maxLines = VIDEO_PROCESSING_ROW_DETAIL_LINES
            itemBinding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) handleChoiceClick(position)
            }
        }

        fun bind(choice: PlayerPanelChoice) {
            val selected = choice.value == currentSelected
            if (itemBinding.root.isActivated != selected) itemBinding.root.isActivated = selected
            itemBinding.optionTitleText.setTextIfChanged(choice.title)
            val hasDetail = choice.detail.isNotBlank()
            itemBinding.optionDetailText.setVisibilityIfChanged(if (hasDetail) View.VISIBLE else View.GONE)
            if (hasDetail) itemBinding.optionDetailText.setTextIfChanged(choice.detail)
            itemBinding.optionCheck.setVisibilityIfChanged(if (selected) View.VISIBLE else View.INVISIBLE)
            bindSwatch(choice.swatchColor)
            if (boundThemeRevision != themeRevision) {
                boundThemeRevision = themeRevision
                itemBinding.root.background = AppCompatResources.getDrawable(
                    itemBinding.root.context,
                    R.drawable.bg_tv_track_row,
                )
            }
            if (refreshFontOnChange) {
                UiFont.applyToTextView(itemBinding.optionTitleText)
                if (hasDetail) UiFont.applyToTextView(itemBinding.optionDetailText)
            }
        }

        private fun bindSwatch(color: Int?) {
            itemBinding.optionColorSwatch.setVisibilityIfChanged(if (color == null) View.GONE else View.VISIBLE)
            if (color == null) return
            val drawable = swatchDrawable ?: GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(Utils.convertDp(itemBinding.root.context, 1f), Color.WHITE)
            }.also {
                swatchDrawable = it
                itemBinding.optionColorSwatch.background = it
            }
            drawable.setColor(color)
        }
    }

    private inner class ToggleViewHolder(
        private val itemBinding: DialogSettingToggleItemBinding,
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        private var boundThemeRevision = 0

        init {
            itemBinding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) handleToggleClick(position, itemBinding.optionToggle)
            }
        }

        fun bind(row: ChoicePanelRow.Toggle) {
            val toggle = row.toggle
            val checked = toggleStates[row.toggleIndex]
            itemBinding.optionToggleTitle.setTextIfChanged(toggle.title)
            val hasDetail = toggle.detail.isNotBlank()
            itemBinding.optionToggleDetail.setVisibilityIfChanged(if (hasDetail) View.VISIBLE else View.GONE)
            if (hasDetail) itemBinding.optionToggleDetail.setTextIfChanged(toggle.detail)
            if (itemBinding.optionToggle.isChecked != checked) itemBinding.optionToggle.isChecked = checked
            if (boundThemeRevision != themeRevision) {
                boundThemeRevision = themeRevision
                val context = itemBinding.root.context
                itemBinding.root.background = AppCompatResources.getDrawable(context, R.drawable.bg_tv_track_row)
                itemBinding.optionToggle.thumbTintList = choiceSwitchThumbTint(context)
                itemBinding.optionToggle.trackTintList = choiceSwitchTrackTint(context)
            }
            if (refreshFontOnChange) {
                UiFont.applyToTextView(itemBinding.optionToggleTitle)
                if (hasDetail) UiFont.applyToTextView(itemBinding.optionToggleDetail)
            }
        }
    }
}

private fun choiceSwitchThumbTint(context: Context): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
    intArrayOf(
        AppearanceTheme.resolveColor(context, R.attr.mpvAccentHot, Color.WHITE),
        ContextCompat.getColor(context, R.color.tv_text_dim),
    ),
)

private fun choiceSwitchTrackTint(context: Context): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
    intArrayOf(
        AppearanceTheme.resolveColor(context, R.attr.mpvAccentDeep, Color.DKGRAY),
        AppearanceTheme.resolveColor(context, R.attr.mpvStrokeStrong, Color.GRAY),
    ),
)

private fun MPVActivity.refreshPlayerChoicePanelTheme(binding: DialogVideoProcessingBinding) {
    binding.root.background = AppCompatResources.getDrawable(this, R.drawable.bg_tv_dialog_shell)
    (binding.videoProcessingList.parent as? View)?.background =
        AppCompatResources.getDrawable(this, R.drawable.bg_tv_panel_card)
    binding.videoProcessingEyebrow.setTextColor(
        AppearanceTheme.resolveColor(this, R.attr.mpvAccentHot, Color.WHITE),
    )
    binding.videoProcessingDoneBtn.background =
        AppCompatResources.getDrawable(this, R.drawable.bg_dialog_action)

    val thumbTint = choiceSwitchThumbTint(this)
    val trackTint = choiceSwitchTrackTint(this)
    repeat(binding.videoProcessingList.childCount) { index ->
        val row = binding.videoProcessingList.getChildAt(index)
        row.background = AppCompatResources.getDrawable(this, R.drawable.bg_tv_track_row)
        row.findViewById<SwitchCompat>(R.id.optionToggle)?.apply {
            thumbTintList = thumbTint
            trackTintList = trackTint
        }
    }
}

private fun String.videoFilterDisplayName(): String {
    return replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

@StringRes
private fun String.videoFilterDetailRes(setting: VideoScalerSetting): Int {
    val details = if (setting == VideoScalerSetting.TEMPORAL) {
        TEMPORAL_FILTER_DETAIL_RES
    } else {
        SPATIAL_FILTER_DETAIL_RES
    }
    val fallback = if (setting == VideoScalerSetting.TEMPORAL) {
        R.string.video_filter_temporal_generic_detail
    } else {
        R.string.video_filter_generic_detail
    }
    return details[this] ?: fallback
}

@StringRes
private fun String.interpolationModeDetailRes(): Int = when (this) {
    "display-resample" -> R.string.video_sync_display_resample_detail
    "display-resample-vdrop" -> R.string.video_sync_display_resample_vdrop_detail
    "display-vdrop" -> R.string.video_sync_display_vdrop_detail
    "display-adrop" -> R.string.video_sync_display_adrop_detail
    else -> R.string.video_processing_interpolation_mode_detail
}
