-dontobfuscate

# Native callbacks are reached through JNI rather than Java references.
-keep class app.mpvnova.player.MPVLib {
    *;
}
