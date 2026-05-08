package app.mpvnova.player

import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

internal fun MPVView.onPointerEvent(event: MotionEvent): Boolean {
    assert(event.isFromSource(InputDevice.SOURCE_CLASS_POINTER))
    if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
        // Scroll-wheel seeking is handled in MPVActivity so it can drive the
        // app's custom seekbar instead of libmpv's default input path.
        return false
    }
    return false
}

internal fun MPVView.onKey(event: KeyEvent): Boolean {
    val mapped = mappedKey(event)
    return when {
        mapped == null -> false
        event.repeatCount > 0 -> true // eat event but ignore it, mpv has its own key repeat
        else -> {
            sendKeyEventToMpv(event, mapped)
            true
        }
    }
}

@Suppress("DEPRECATION")
private fun mappedKey(event: KeyEvent): String? {
    val mapped = KeyMapping.map.get(event.keyCode)
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

private fun sendKeyEventToMpv(event: KeyEvent, mapped: String) {
    val mod: MutableList<String> = mutableListOf()
    event.isShiftPressed && mod.add("shift")
    event.isCtrlPressed && mod.add("ctrl")
    event.isAltPressed && mod.add("alt")
    event.isMetaPressed && mod.add("meta")

    val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
    mod.add(mapped)
    mpvCommand(arrayOf(action, mod.joinToString("+")))
}
