package app.mpvnova.player

import android.view.ViewGroup
import androidx.core.view.isVisible
import app.mpvnova.player.databinding.DialogMediaPickerAudioFiltersBinding
import app.mpvnova.player.databinding.DialogMediaPickerBinding
import app.mpvnova.player.databinding.DialogMediaPickerSubFiltersBinding

internal fun DialogMediaPickerBinding.positionDelayRow(
    options: MediaPickerDialog.Options,
    audioBinding: DialogMediaPickerAudioFiltersBinding?,
    subBinding: DialogMediaPickerSubFiltersBinding?,
) {
    if (!options.showDelay) return
    val anchor = when {
        options.showFilters -> audioBinding?.persistFiltersRow
        subBinding?.onlineSubtitleActions?.isVisible == true -> subBinding.onlineSubtitleActions
        options.showSubFilters -> subBinding?.persistSubFiltersRow
        else -> null
    }
    val targetParent = (anchor?.parent as? ViewGroup) ?: filterGroup
    val desiredIndex = if (anchor != null) targetParent.indexOfChild(anchor) + 1 else targetParent.childCount
    val currentParent = delayRow.parent as? ViewGroup
    if (currentParent === targetParent) {
        val currentIndex = targetParent.indexOfChild(delayRow)
        val normalizedTarget = if (currentIndex < desiredIndex) desiredIndex - 1 else desiredIndex
        if (currentIndex == normalizedTarget) return
        targetParent.removeView(delayRow)
        targetParent.addView(delayRow, normalizedTarget)
    } else {
        currentParent?.removeView(delayRow)
        targetParent.addView(delayRow, desiredIndex.coerceIn(0, targetParent.childCount))
    }
}
