package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

internal fun MPVActivity.skipButtonVerticalTarget(
    ev: KeyEvent,
    controls: List<View>,
    current: View?,
    seekbarSelected: Boolean,
): Int? {
    val isUp = ev.keyCode == KeyEvent.KEYCODE_DPAD_UP
    val seekbarBelow = playerUiCustomization.seekbarPosition == PlayerSeekbarPosition.BELOW
    val isPlayerBarButton = current in controls && current !== binding.playbackSeekbar &&
        (topActionsInPlayerBar || (current !== binding.topMenuBtn && current !== binding.topPiPBtn))
    val atUpperPlayerBarRow = if (seekbarBelow) isPlayerBarButton else seekbarSelected
    var target: Int? = null
    if (current === binding.skipSegmentBtn) {
        target = if (isUp) {
            -1
        } else {
            upperPlayerBarControlIndex(controls)
        }
    } else if (atUpperPlayerBarRow && isUp && skipButtonVisible) {
        target = SKIP_BUTTON_SELECTION_INDEX
    }
    return target
}
