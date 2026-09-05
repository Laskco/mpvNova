package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import app.mpvnova.player.databinding.DialogSubStyleSavePresetBinding

private const val SUB_STYLE_PRESET_NAME_MAX_CHARS = 12

internal fun MPVActivity.openSavePresetPrompt() {
    val restore = keepPlaybackForDialog()
    val view = DialogSubStyleSavePresetBinding.inflate(LayoutInflater.from(this))
    val editing = editingSubtitleStylePreset
    view.presetDialogTitle.setText(
        if (editing != null) R.string.sub_style_edit_preset_title else R.string.sub_style_save_preset_title
    )
    view.presetNameInput.setText(editing?.name.orEmpty())
    view.presetNameInput.setSelection(view.presetNameInput.text?.length ?: 0)
    fun syncNameCount() {
        val length = view.presetNameInput.text?.length ?: 0
        view.presetNameCount.text = getString(
            R.string.sub_style_preset_name_count,
            length.coerceAtMost(SUB_STYLE_PRESET_NAME_MAX_CHARS),
            SUB_STYLE_PRESET_NAME_MAX_CHARS,
        )
    }
    view.presetNameInput.addTextChangedListener(afterTextChanged = { syncNameCount() })
    syncNameCount()
    val layoutController = SubtitlePresetLayoutController(this, view, editing)
    val selection = PresetOverrideSelection(
        overrideAss = editing?.overrideAss ?: subStyleOverrideAss,
        selectiveAss = editing?.selectiveAss ?: subStyleSelectiveAss,
        forceAll = editing?.forceAll ?: subStyleForceAllAss,
    )
    var accepted = false

    layoutController.bind()
    bindPresetOverrideRows(view, selection)

    val dialog = with(AlertDialog.Builder(this)) {
        setView(view.root)
        setOnDismissListener {
            if (!accepted) layoutController.restore()
            restore()
            openSubtitleStyleDialog()
        }
        create()
    }
    view.presetCancelBtn.setOnClickListener { dialog.dismiss() }
    view.presetSaveBtn.setOnClickListener {
        val name = view.presetNameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            view.presetNameInput.error = getString(R.string.sub_style_preset_name_required)
        } else {
            val toastTitle = getString(
                if (editing != null) R.string.sub_style_preset_updated else R.string.sub_style_preset_saved
            )
            accepted = true
            layoutController.accept()
            saveSubtitleStylePreset(
                name = name,
                originalName = editing?.name,
                includeLayout = layoutController.includeLayout,
                overrideAss = selection.overrideAss,
                selectiveAss = selection.selectiveAss,
                forceAll = selection.forceAll,
                scaleLevel = layoutController.scaleLevel,
                posPct = layoutController.posPct,
            )
            editingSubtitleStylePreset = null
            showToast(toastTitle, name)
            dialog.dismiss()
        }
    }
    showWidePlayerDialog(dialog, SAVE_PRESET_DIALOG_LAYOUT)
}

private fun MPVActivity.saveSubtitleStylePreset(
    name: String,
    originalName: String?,
    includeLayout: Boolean,
    overrideAss: Boolean,
    selectiveAss: Boolean,
    forceAll: Boolean,
    scaleLevel: Int,
    posPct: Int,
) {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val preset = captureSubtitleStylePreset(
        name, includeLayout, overrideAss, selectiveAss, forceAll, scaleLevel, posPct,
    )
    val updated = loadSubtitleStylePresets(prefs).filterNot {
        it.name.equals(name, ignoreCase = true) ||
            originalName?.let { oldName -> it.name.equals(oldName, ignoreCase = true) } == true
    } + preset
    saveSubtitleStylePresets(prefs, updated)
    subStylePresetIndex = updated.indexOfFirst { it.name.equals(name, ignoreCase = true) }.coerceAtLeast(0)
}

internal fun MPVActivity.hasSubtitleStylePresets(): Boolean =
    loadSubtitleStylePresets(getDefaultSharedPreferences(applicationContext)).isNotEmpty()

internal fun MPVActivity.currentSubtitleStylePresetName(): String {
    val presets = loadSubtitleStylePresets(getDefaultSharedPreferences(applicationContext))
    if (presets.isEmpty() || !customSubStyleEnabled)
        return getString(R.string.sub_style_preset_none)
    return presets[subStylePresetIndex.coerceIn(0, presets.lastIndex)].name
}

// Cycle to the next/prev saved preset, apply it live, and return its name for the UI.
internal fun MPVActivity.cycleSubtitleStylePreset(delta: Int): String {
    val presets = loadSubtitleStylePresets(getDefaultSharedPreferences(applicationContext))
    val selectedName = if (presets.isEmpty()) {
        getString(R.string.sub_style_preset_none)
    } else {
        val presetCycleSize = presets.size + 1
        val currentCycleIndex = if (customSubStyleEnabled) {
            subStylePresetIndex.coerceIn(0, presets.lastIndex) + 1
        } else {
            0
        }
        val nextCycleIndex = wrapPresetCycleIndex(currentCycleIndex, delta, presetCycleSize)
        if (nextCycleIndex == 0) {
            val grayImageSubsChanged = subStyleGrayImageSubs
            customSubStyleEnabled = false
            subStyleGrayImageSubs = false
            applyCustomSubtitleStyle()
            if (grayImageSubsChanged)
                rebuildSelectedImageSubtitleTracks()
            subScaleLevel = DEFAULT_SUB_SCALE_INDEX
            subPosLevel = DEFAULT_SUB_POSITION_INDEX
            applySubScaleProperty()
            applySubPosProperty()
            writeSettings()
            writeSubtitleStyleSettings()
            getString(R.string.sub_style_preset_none)
        } else {
            subStylePresetIndex = nextCycleIndex - 1
            applySubtitleStylePreset(presets[subStylePresetIndex])
            presets[subStylePresetIndex].name
        }
    }
    return selectedName
}

internal fun MPVActivity.showEditPresetDialog() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val presets = loadSubtitleStylePresets(prefs)
    if (presets.isEmpty()) {
        openSubtitleStyleDialog()
        return
    }
    val restore = keepPlaybackForDialog()
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_style_edit_preset)
        setItems(presets.map { it.name }.toTypedArray()) { d, which ->
            val preset = presets[which]
            editingSubtitleStylePreset = preset
            subStylePresetIndex = which
            applySubtitleStylePreset(preset)
            showToast(getString(R.string.sub_style_edit_preset), preset.name)
            d.dismiss()
        }
        setOnDismissListener {
            restore()
            openSubtitleStyleDialog()
        }
        create()
    }
    showPlayerDialog(dialog)
}

internal fun MPVActivity.showApplyPresetDialog() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val presets = loadSubtitleStylePresets(prefs)
    if (presets.isEmpty()) {
        openSubtitleStyleDialog()
        return
    }
    val restore = keepPlaybackForDialog()
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_style_apply_preset)
        setItems(presets.map { it.name }.toTypedArray()) { d, which ->
            val preset = presets[which]
            editingSubtitleStylePreset = null
            subStylePresetIndex = which
            applySubtitleStylePreset(preset)
            showToast(getString(R.string.sub_style_preset_applied), preset.name)
            d.dismiss()
        }
        setOnDismissListener {
            restore()
            openSubtitleStyleDialog()
        }
        create()
    }
    showPlayerDialog(dialog)
}

internal fun MPVActivity.showDeletePresetDialog() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val presets = loadSubtitleStylePresets(prefs)
    if (presets.isEmpty()) {
        openSubtitleStyleDialog()
        return
    }
    val restore = keepPlaybackForDialog()
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_style_delete_preset)
        setItems(presets.map { it.name }.toTypedArray()) { d, which ->
            val target = presets[which]
            saveSubtitleStylePresets(prefs, presets.filterNot { it.name == target.name })
            if (editingSubtitleStylePreset?.name == target.name) {
                editingSubtitleStylePreset = null
            }
            subStylePresetIndex = subStylePresetIndex.coerceAtMost((presets.size - 2).coerceAtLeast(0))
            showToast(getString(R.string.sub_style_preset_removed), target.name)
            d.dismiss()
        }
        setOnDismissListener {
            restore()
            openSubtitleStyleDialog()
        }
        create()
    }
    showPlayerDialog(dialog)
}

private fun wrapPresetCycleIndex(current: Int, delta: Int, size: Int): Int =
    ((current + delta) % size + size) % size

private fun MPVActivity.captureSubtitleStylePreset(
    name: String,
    includeLayout: Boolean,
    overrideAss: Boolean,
    selectiveAss: Boolean,
    forceAll: Boolean,
    scaleLevel: Int,
    posPct: Int,
) = SubtitleStylePreset(
    name = name,
    textColorId = SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].id,
    textOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex],
    borderColorId = SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].id,
    borderSizeIndex = subStyleBorderSizeIndex,
    blurIndex = subStyleBlurIndex,
    shadowSizeIndex = subStyleShadowSizeIndex,
    shadowColorId = SUBTITLE_COLOR_OPTIONS[subStyleShadowColorIndex].id,
    bgColorId = SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].id,
    bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex],
    edge = subStyleEdge.name,
    fontFamily = subStyleFontFamily,
    bold = subStyleBold,
    italic = subStyleItalic,
    spacingIndex = subStyleSpacingIndex,
    justify = subStyleJustify.name,
    overrideAss = overrideAss,
    selectiveAss = selectiveAss,
    forceAll = forceAll,
    grayImageSubs = subStyleGrayImageSubs,
    includeLayout = includeLayout,
    scaleLevel = scaleLevel.coerceIn(0, subScaleSteps.lastIndex),
    posPct = posPct.coerceIn(SUB_POSITION_MIN_PERCENT, SUB_POSITION_MAX_PERCENT),
    extras = subStyleExtras.normalized(),
)

private fun MPVActivity.applySubtitleStylePreset(p: SubtitleStylePreset) {
    subStyleExtras = p.extras.normalized()
    val grayImageSubsChanged = subStyleGrayImageSubs != p.grayImageSubs
    subStyleTextColorIndex = subtitleColorOptionIndex(p.textColorId)
    subStyleTextOpacityIndex = nearestOpacityIndex(p.textOpacity)
    subStyleBorderColorIndex = subtitleColorOptionIndex(p.borderColorId)
    subStyleBorderSizeIndex = p.borderSizeIndex.coerceIn(0, SUBTITLE_BORDER_SIZE_STEPS.lastIndex)
    subStyleBlurIndex = p.blurIndex.coerceIn(0, SUBTITLE_BLUR_STEPS.lastIndex)
    subStyleShadowSizeIndex = p.shadowSizeIndex.coerceIn(0, SUBTITLE_SHADOW_SIZE_STEPS.lastIndex)
    subStyleShadowColorIndex = subtitleColorOptionIndex(p.shadowColorId)
    subStyleBgColorIndex = subtitleColorOptionIndex(p.bgColorId)
    subStyleBgOpacityIndex = nearestOpacityIndex(p.bgOpacity)
    subStyleEdge = runCatching { SubtitleEdgeStyle.valueOf(p.edge) }.getOrDefault(DEFAULT_SUBTITLE_EDGE_STYLE)
    subStyleFontFamily = p.fontFamily.ifEmpty { SUBTITLE_FONT_DEFAULT_FAMILY }
    subStyleBold = p.bold
    subStyleItalic = p.italic
    subStyleSpacingIndex = p.spacingIndex.coerceIn(0, SUBTITLE_SPACING_STEPS.lastIndex)
    subStyleJustify = runCatching { SubtitleJustify.valueOf(p.justify) }.getOrDefault(DEFAULT_SUBTITLE_JUSTIFY)
    subStyleOverrideAss = p.overrideAss
    subStyleSelectiveAss = p.selectiveAss
    subStyleForceAllAss = p.forceAll
    subStyleGrayImageSubs = p.grayImageSubs
    normalizeAssOverrideModes()
    customSubStyleEnabled = true
    if (p.includeLayout) {
        subScaleLevel = p.scaleLevel.coerceIn(0, subScaleSteps.lastIndex)
        subPosLevel = nearestSubPositionIndex(subPosSteps, p.posPct)
    }
    applyCustomSubtitleStyle()
    if (grayImageSubsChanged)
        rebuildSelectedImageSubtitleTracks()
    if (p.includeLayout) {
        applySubScaleProperty()
        applySubPosProperty()
    }
    writeSubtitleStyleSettings()
}
