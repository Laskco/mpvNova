package app.mpvnova.player

import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent

internal fun MPVView.onKey(event: KeyEvent): Boolean {
    val mapped = mpvKeyNameForEvent(event)
    if (mapped == null) return false

    return when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            val key = mpvKeyWithModifiers(event, mapped)
            if (heldMpvKeys.press(event.keyCode, key)) {
                mpvCommand(arrayOf("keydown", key))
            }
            // Android repeats are consumed here because mpv repeats a held key itself.
            true
        }
        KeyEvent.ACTION_UP -> {
            val key = heldMpvKeys.release(event.keyCode) ?: mpvKeyWithModifiers(event, mapped)
            mpvCommand(arrayOf("keyup", key))
            true
        }
        else -> false
    }
}

/**
 * Releases a key that was previously forwarded to mpv before another UI handler can consume
 * its ACTION_UP. This matters when a held D-pad key changes the visible controls or focus state.
 */
internal fun MPVView.releaseMpvKey(event: KeyEvent): Boolean =
    if (event.action != KeyEvent.ACTION_UP) {
        false
    } else {
        heldMpvKeys.release(event.keyCode)?.let { key ->
            mpvCommand(arrayOf("keyup", key))
            true
        } ?: false
    }

internal fun MPVView.releaseAllMpvKeys() {
    if (!heldMpvKeys.clear()) return
    // mpv documents keyup without a key name as releasing every held key.
    mpvCommand(arrayOf("keyup"))
}

@Suppress("DEPRECATION")
internal fun mpvKeyNameForEvent(event: KeyEvent): String? {
    val mapped = keyMapping[event.keyCode]
    return when {
        event.action == KeyEvent.ACTION_MULTIPLE -> null
        KeyEvent.isModifierKey(event.keyCode) -> null
        mapped != null -> mapped
        else -> printableKey(event)
    }
}

private fun printableKey(event: KeyEvent): String? {
    if (!event.isPrintingKey) {
        if (event.repeatCount == 0)
            Log.d(MPV_VIEW_LOG_TAG, "Unmapped non-printable key ${event.keyCode}")
        return null
    }
    val char = event.unicodeChar
    return if (char.and(KeyCharacterMap.COMBINING_ACCENT) == 0) char.toChar().toString() else null
}

private fun mpvKeyWithModifiers(event: KeyEvent, mapped: String): String {
    val mod: MutableList<String> = mutableListOf()
    event.isShiftPressed && mod.add("shift")
    event.isCtrlPressed && mod.add("ctrl")
    event.isAltPressed && mod.add("alt")
    event.isMetaPressed && mod.add("meta")
    mod.add(mapped)
    return mod.joinToString("+")
}

internal class MpvHeldKeyTracker {
    private val keys = linkedMapOf<Int, String>()

    fun press(keyCode: Int, mpvKey: String): Boolean {
        if (keys.containsKey(keyCode)) return false
        keys[keyCode] = mpvKey
        return true
    }

    fun release(keyCode: Int): String? = keys.remove(keyCode)

    /** Returns whether any held keys were cleared. */
    fun clear(): Boolean {
        if (keys.isEmpty()) return false
        keys.clear()
        return true
    }
}
