package app.mpvnova.player

/**
 * Picker UI state snapshots for the subtitle controls (sub scale, sub
 * position, secondary sub position, secondary sub picker). Each value
 * gets a [MediaPickerDialog.ValueState] describing whether the +/- buttons
 * can move further, whether the slot is in the "on" state, and the label
 * text the picker should render.
 *
 * Read-only state shaping: the actual property writes that change subtitle
 * scale/position live in MPVActivitySubtitleApply.kt.
 */

internal fun MPVActivity.currentSubScaleState(): MediaPickerDialog.ValueState {
    val maxLevel = subScaleSteps.lastIndex
    return MediaPickerDialog.ValueState(
        label = getSubScaleLabel(),
        active = isSubScaleOn(),
        canDecrease = subScaleLevel > 0,
        canIncrease = subScaleLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSubPosState(): MediaPickerDialog.ValueState {
    val maxLevel = subPosSteps.lastIndex
    return MediaPickerDialog.ValueState(
        label = getSubPosLabel(),
        active = isSubPosOn(),
        canDecrease = subPosLevel > 0,
        canIncrease = subPosLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSecondaryPosState(): MediaPickerDialog.ValueState {
    val maxLevel = secondaryPosSteps.lastIndex
    // Secondary subs only render when a secondary track is on, so the
    // position controls are useless otherwise — dim and disable them
    // until the user actually enables a secondary track.
    val secondaryOn = player.secondarySid != -1
    return MediaPickerDialog.ValueState(
        label = getSecondaryPosLabel(),
        active = isSecondaryPosOn() && secondaryOn,
        enabled = secondaryOn,
        canDecrease = secondaryOn && secondaryPosLevel > 0,
        canIncrease = secondaryOn && secondaryPosLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSubWidthState(): MediaPickerDialog.ValueState {
    val maxLevel = subWidthSteps.lastIndex
    return MediaPickerDialog.ValueState(
        label = getSubWidthLabel(),
        active = isSubWidthOn(),
        canDecrease = subWidthLevel > 0,
        canIncrease = subWidthLevel < maxLevel,
    )
}

internal fun MPVActivity.currentSecondarySubState(): MediaPickerDialog.ValueState {
    val available = availableSecondarySubTracks()
    if (available.isEmpty()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.sub_secondary_unavailable),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false,
        )
    }
    val currentSid = player.secondarySid
    val on = currentSid != -1 && available.any { it.mpvId == currentSid }
    return MediaPickerDialog.ValueState(
        label = if (on) "#$currentSid" else getString(R.string.status_off),
        active = on,
        // +/- now cycles through Off → every available track → back to Off,
        // so both directions are always available when there's at least
        // one alternate track to choose from.
        canDecrease = true,
        canIncrease = true,
    )
}

internal fun MPVActivity.currentSubFilterStates(): MediaPickerDialog.SubFilterStates {
    return MediaPickerDialog.SubFilterStates(
        subScale = currentSubScaleState(),
        subPos = currentSubPosState(),
        secondaryPos = currentSecondaryPosState(),
        subWidth = currentSubWidthState(),
        secondarySub = currentSecondarySubState(),
    )
}
