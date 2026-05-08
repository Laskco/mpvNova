package app.mpvnova.player

private const val REPEAT_NONE = 0
private const val REPEAT_FILE = 1
private const val REPEAT_PLAYLIST = 2

internal fun MPVView.getRepeat(): Int {
    return when (mpvGetPropertyString("loop-playlist") + mpvGetPropertyString("loop-file")) {
        "noinf" -> REPEAT_PLAYLIST
        "infno" -> REPEAT_FILE
        else -> REPEAT_NONE
    }
}

internal fun MPVView.cycleRepeat() {
    when (val state = getRepeat()) {
        REPEAT_NONE, REPEAT_FILE -> {
            mpvSetPropertyString("loop-playlist", if (state == REPEAT_FILE) "no" else "inf")
            mpvSetPropertyString("loop-file", if (state == REPEAT_FILE) "inf" else "no")
        }
        REPEAT_PLAYLIST -> mpvSetPropertyString("loop-file", "no")
    }
}

internal fun MPVView.getShuffle(): Boolean {
    return mpvGetPropertyBoolean("shuffle") == true
}

internal fun MPVView.changeShuffle(cycle: Boolean, value: Boolean = true) {
    // Use the 'shuffle' property to store the shuffled state, since changing it at runtime doesn't do anything.
    val state = getShuffle()
    val newState = if (cycle) state.xor(value) else value
    if (state == newState)
        return
    mpvCommand(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
    mpvSetPropertyBoolean("shuffle", newState)
}
