package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.openSubtitleStyleDialog() {
    val restore = keepPlaybackForDialog()
    val impl = subtitleStyleDialog ?: SubtitleStyleDialog().also {
        subtitleStyleDialog = it
    }
    impl.stateProvider = { subtitleStyleState() }
    impl.onAdjust = { control, delta -> adjustSubtitleStyle(control, delta) }
    lateinit var dialog: AlertDialog
    impl.onAddFont = {
        dialog.dismiss()
        pickAndImportSubtitleFont()
    }
    impl.onRemoveFont = {
        dialog.dismiss()
        showRemoveSubtitleFontDialog()
    }
    impl.onSavePreset = {
        dialog.dismiss()
        openSavePresetPrompt()
    }
    impl.onApplyPreset = {
        dialog.dismiss()
        showApplyPresetDialog()
    }
    impl.onEditPreset = {
        dialog.dismiss()
        showEditPresetDialog()
    }
    impl.onDeletePreset = {
        dialog.dismiss()
        showDeletePresetDialog()
    }

    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.6f,
            maxWidthDp = 820f,
            heightFraction = 0.9f,
            maxHeightDp = 880f,
        )
    )
}

private fun MPVActivity.pickAndImportSubtitleFont() {
    openFilePickerFor(R.string.sub_style_add_font) { result, data ->
        val family = importSubtitleFont(result, data)
        if (!family.isNullOrEmpty()) {
            subStyleFontFamily = family
            if (customSubStyleEnabled)
                applyCustomSubtitleStyle()
            writeSubtitleStyleSettings()
            showToast(getString(R.string.sub_style_font_added), family)
        }
        openSubtitleStyleDialog()
    }
}

private fun MPVActivity.showRemoveSubtitleFontDialog() {
    val families = removableFontFamilies()
    if (families.isEmpty()) {
        showToast(
            getString(R.string.sub_style_remove_font),
            getString(R.string.sub_style_no_user_fonts),
        )
        openSubtitleStyleDialog()
        return
    }
    val restore = keepPlaybackForDialog()
    val items = families.toTypedArray()
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_style_remove_font)
        setItems(items) { d, which ->
            val family = families[which]
            removeSubtitleFontFamily(family)
            showToast(getString(R.string.sub_style_font_removed), family)
            d.dismiss()
        }
        setOnDismissListener { restore(); openSubtitleStyleDialog() }
        create()
    }
    showPlayerDialog(dialog)
}

internal fun MPVActivity.subtitleStyleState(): SubtitleStyleDialog.State {
    val on = customSubStyleEnabled
    val overrideRows = assOverrideRowStates(on)
    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    val bgOn = bgOpacity > 0
    // Outline/edge and background box are mutually exclusive rendering modes.
    val edgeApplies = on && !bgOn
    val shadowApplies = edgeApplies && subStyleEdge == SubtitleEdgeStyle.DROP_SHADOW

    return SubtitleStyleDialog.State(
        title = editingSubtitleStylePreset?.name?.let {
            getString(R.string.sub_style_preset_editing, it)
        } ?: getString(R.string.sub_style_title),
        masterOn = on,
        imageSubtitleGrayOn = subStyleGrayImageSubs,
        textColor = colorRow(subStyleTextColorIndex, on),
        textOpacity = SubtitleStyleDialog.Row(
            percentLabel(SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex]),
            enabled = on,
        ),
        edge = SubtitleStyleDialog.Row(edgeLabel(subStyleEdge), enabled = edgeApplies),
        outlineColor = colorRow(subStyleBorderColorIndex, edgeApplies && subStyleEdge != SubtitleEdgeStyle.NONE),
        outlineSize = SubtitleStyleDialog.Row(
            "%.0f".format(SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex]),
            enabled = edgeApplies && subStyleEdge != SubtitleEdgeStyle.NONE,
        ),
        blur = SubtitleStyleDialog.Row(
            if (subStyleBlurIndex == 0) getString(R.string.status_off)
            else "%.1f".format(SUBTITLE_BLUR_STEPS[subStyleBlurIndex]),
            enabled = on,
        ),
        shadowSize = SubtitleStyleDialog.Row(
            "%.0f".format(SUBTITLE_SHADOW_SIZE_STEPS[subStyleShadowSizeIndex]),
            enabled = shadowApplies,
        ),
        shadowColor = colorRow(subStyleShadowColorIndex, shadowApplies),
        bgOpacity = SubtitleStyleDialog.Row(
            if (bgOn) percentLabel(bgOpacity) else getString(R.string.status_off),
            enabled = on,
        ),
        bgColor = colorRow(subStyleBgColorIndex, on && bgOn),
        font = SubtitleStyleDialog.Row(subtitleFontLabel(subStyleFontFamily), enabled = on),
        spacing = SubtitleStyleDialog.Row(
            if (subStyleSpacingIndex == 0) getString(R.string.status_off)
            else "%.0f".format(SUBTITLE_SPACING_STEPS[subStyleSpacingIndex]),
            enabled = on,
        ),
        justify = SubtitleStyleDialog.Row(justifyLabel(subStyleJustify), enabled = on),
        boldOn = subStyleBold,
        italicOn = subStyleItalic,
        overrideOn = subStyleOverrideAss,
        overrideEnabled = overrideRows.overrideEnabled,
        selectiveOn = subStyleSelectiveAss,
        selectiveEnabled = overrideRows.selectiveEnabled,
        forceAllOn = subStyleForceAllAss,
        forceAllEnabled = overrideRows.forceAllEnabled,
        preview = subtitleStylePreviewSpec(),
    )
}

private data class AssOverrideRowStates(
    val overrideEnabled: Boolean,
    val selectiveEnabled: Boolean,
    val forceAllEnabled: Boolean,
)

// The three ASS-override rows are mutually exclusive: a row is clickable only when it is the
// active mode (so it can be toggled off) or no other mode is active.
private fun MPVActivity.assOverrideRowStates(masterOn: Boolean): AssOverrideRowStates {
    val o = subStyleOverrideAss
    val s = subStyleSelectiveAss
    val f = subStyleForceAllAss
    return AssOverrideRowStates(
        overrideEnabled = masterOn && (o || !(s || f)),
        selectiveEnabled = masterOn && (s || !(o || f)),
        forceAllEnabled = masterOn && (f || !(o || s)),
    )
}

private fun MPVActivity.colorRow(index: Int, enabled: Boolean): SubtitleStyleDialog.Row {
    val option = SUBTITLE_COLOR_OPTIONS[index]
    return SubtitleStyleDialog.Row(option.label, enabled = enabled, chipRgb = option.rgb)
}

private fun percentLabel(percent: Int): String = "$percent%"

private fun MPVActivity.edgeLabel(edge: SubtitleEdgeStyle): String = getString(
    when (edge) {
        SubtitleEdgeStyle.OUTLINE -> R.string.sub_style_edge_outline
        SubtitleEdgeStyle.DROP_SHADOW -> R.string.sub_style_edge_shadow
        SubtitleEdgeStyle.NONE -> R.string.sub_style_edge_none
    }
)

private fun MPVActivity.justifyLabel(justify: SubtitleJustify): String = getString(
    when (justify) {
        SubtitleJustify.AUTO -> R.string.sub_style_justify_auto
        SubtitleJustify.LEFT -> R.string.sub_style_justify_left
        SubtitleJustify.CENTER -> R.string.sub_style_justify_center
        SubtitleJustify.RIGHT -> R.string.sub_style_justify_right
    }
)
