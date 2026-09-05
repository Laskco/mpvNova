package app.mpvnova.player

import android.app.Activity
import app.mpvnova.player.preferences.SettingsChoiceItem
import app.mpvnova.player.preferences.showSettingsChoiceDialog

internal class PlayerAppearanceEditHistory<T>(private val initial: T) {
    private val states = mutableListOf(initial)
    private var position = 0

    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < states.lastIndex
    val canRevert: Boolean get() = states[position] != initial

    fun record(value: T) {
        if (value == states[position]) return
        states.subList(position + 1, states.size).clear()
        states.add(value)
        if (states.size > MAX_EDITOR_HISTORY) states.removeAt(0)
        position = states.lastIndex
    }

    fun undo(): T {
        if (canUndo) position--
        return states[position]
    }

    fun redo(): T {
        if (canRedo) position++
        return states[position]
    }

    fun revert(): T {
        record(initial)
        return initial
    }
}

internal fun <T> Activity.showAppearanceEditorActions(
    history: PlayerAppearanceEditHistory<T>,
    applyStyle: (T) -> Unit,
    copyActions: List<SettingsChoiceItem>,
    exportPreset: () -> Unit,
    importPreset: () -> Unit,
) {
    val items = listOf(
        SettingsChoiceItem(getString(R.string.appearance_editor_undo), enabled = history.canUndo) {
            applyStyle(history.undo())
        },
        SettingsChoiceItem(getString(R.string.appearance_editor_redo), enabled = history.canRedo) {
            applyStyle(history.redo())
        },
        SettingsChoiceItem(getString(R.string.appearance_editor_revert), enabled = history.canRevert) {
            applyStyle(history.revert())
        },
    ) + copyActions + listOf(
        SettingsChoiceItem(getString(R.string.appearance_editor_export), onClick = exportPreset),
        SettingsChoiceItem(getString(R.string.appearance_editor_import), onClick = importPreset),
    )
    showSettingsChoiceDialog(getString(R.string.appearance_editor_actions), items)
}

private const val MAX_EDITOR_HISTORY = 64
