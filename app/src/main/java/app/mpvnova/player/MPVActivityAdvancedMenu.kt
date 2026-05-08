package app.mpvnova.player

import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.advancedMenuItems(restoreState: StateRestoreCallback): MutableList<MenuItem> {
    val buttons = mutableListOf(
        MenuItem(R.id.subSeekPrev) { mpvCommand(arrayOf("sub-seek", "-1")); true },
        MenuItem(R.id.subSeekNext) { mpvCommand(arrayOf("sub-seek", "1")); true },
        MenuItem(R.id.statsBtn) {
            mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle")); true
        },
        MenuItem(R.id.aspectBtn) { openAspectMenu(restoreState) },
    )
    addStatsPageButtons(buttons)
    addVideoAdjustmentButtons(buttons, restoreState)
    addDelayButtons(buttons, restoreState)
    return buttons
}

private fun MPVActivity.openAspectMenu(restoreState: StateRestoreCallback): Boolean {
    val ratios = resources.getStringArray(R.array.aspect_ratios)
    with(AlertDialog.Builder(this)) {
        setItems(R.array.aspect_ratio_names) { dialog, item ->
            applyAspectRatioChoice(ratios[item])
            dialog.dismiss()
        }
        setOnDismissListener { restoreState() }
        create().show()
    }
    return false
}

private fun applyAspectRatioChoice(ratio: String) {
    if (ratio == "panscan") {
        mpvSetPropertyString("video-aspect-override", "-1")
        mpvSetPropertyDouble("panscan", 1.0)
    } else {
        mpvSetPropertyString("video-aspect-override", ratio)
        mpvSetPropertyDouble("panscan", 0.0)
    }
}

private fun MPVActivity.addStatsPageButtons(buttons: MutableList<MenuItem>) {
    val statsButtons = arrayOf(R.id.statsBtn1, R.id.statsBtn2, R.id.statsBtn3)
    for (page in STATS_PAGE_FIRST..STATS_PAGE_LAST) {
        buttons.add(MenuItem(statsButtons[page - 1]) {
            mpvCommand(arrayOf("script-binding", "stats/display-page-$page")); true
        })
    }
}

private fun MPVActivity.addVideoAdjustmentButtons(
    buttons: MutableList<MenuItem>,
    restoreState: StateRestoreCallback
) {
    val ids = arrayOf(R.id.contrastBtn, R.id.brightnessBtn, R.id.gammaBtn, R.id.saturationBtn)
    val props = arrayOf("contrast", "brightness", "gamma", "saturation")
    val titles = arrayOf(R.string.contrast, R.string.video_brightness, R.string.gamma, R.string.saturation)
    ids.forEachIndexed { index, id ->
        buttons.add(MenuItem(id) { openVideoAdjustmentPicker(titles[index], props[index], restoreState) })
    }
}

private fun MPVActivity.openVideoAdjustmentPicker(
    titleRes: Int,
    property: String,
    restoreState: StateRestoreCallback
): Boolean {
    val slider = SliderPickerDialog(
        VIDEO_ADJUSTMENT_MIN,
        VIDEO_ADJUSTMENT_MAX,
        VIDEO_ADJUSTMENT_STEP,
        R.string.format_fixed_number
    )
    genericPickerDialog(slider, titleRes, property, restoreState)
    return false
}

private fun MPVActivity.addDelayButtons(
    buttons: MutableList<MenuItem>,
    restoreState: StateRestoreCallback
) {
    buttons.add(MenuItem(R.id.audioDelayBtn) {
        val picker = DecimalPickerDialog(AUDIO_DELAY_MIN_SEC, AUDIO_DELAY_MAX_SEC)
        genericPickerDialog(picker, R.string.audio_delay, "audio-delay", restoreState)
        false
    })
    buttons.add(MenuItem(R.id.subDelayBtn) {
        openAdvancedSubDelayDialog(restoreState)
        false
    })
}

private fun MPVActivity.openAdvancedSubDelayDialog(restoreState: StateRestoreCallback) {
    val picker = SubDelayDialog(SUB_DELAY_MIN_SEC, SUB_DELAY_MAX_SEC)
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_delay)
        val inflater = layoutInflater
        setView(picker.buildView(inflater))
        setPositiveButton(R.string.dialog_ok) { _, _ ->
            picker.delay1?.let { player.subDelay = it }
            picker.delay2?.let { player.secondarySubDelay = it }
        }
        setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
        setOnDismissListener { restoreState() }
        create()
    }

    picker.delay1 = player.subDelay ?: 0.0
    picker.delay2 = if (player.secondarySid != -1) player.secondarySubDelay else null
    showWidePlayerDialog(dialog, advancedSubDelayLayout())
}

private fun advancedSubDelayLayout(): PlayerDialogLayout {
    return PlayerDialogLayout(
        widthFraction = ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION,
        maxWidthDp = ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP,
        heightFraction = ADVANCED_SUB_DELAY_DIALOG_HEIGHT_FRACTION,
        maxHeightDp = ADVANCED_SUB_DELAY_DIALOG_MAX_HEIGHT_DP,
    )
}

internal fun MPVActivity.advancedHiddenButtons(): MutableSet<Int> {
    val hiddenButtons = mutableSetOf<Int>()
    if (player.vid == -1)
        hiddenButtons.addAll(arrayOf(R.id.rowVideo1, R.id.rowVideo2, R.id.aspectBtn))
    if (player.aid == -1 || player.vid == -1)
        hiddenButtons.add(R.id.audioDelayBtn)
    if (player.sid == -1)
        hiddenButtons.addAll(arrayOf(R.id.subDelayBtn, R.id.rowSubSeek))
    return hiddenButtons
}
