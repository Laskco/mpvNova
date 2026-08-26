package app.mpvnova.player

import android.view.ViewGroup
import app.mpvnova.player.databinding.DialogMediaPickerBinding

internal fun DialogMediaPickerBinding.positionDelayRow(options: MediaPickerDialog.Options) {
    if (options.showDelay) {
        val parent = delayRow.parent as? ViewGroup
        if (parent != null) {
            val anchor = if (options.showFilters) persistFiltersRow else persistSubFiltersRow
            val targetIndex = parent.indexOfChild(anchor) + 1
            if (parent.indexOfChild(delayRow) != targetIndex) {
                parent.removeView(delayRow)
                parent.addView(delayRow, targetIndex)
            }
        }
    }
}
