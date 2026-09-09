-dontobfuscate

# AppCompat instantiates this class by its name in the theme.
-keep class app.mpvnova.player.OutlinedAppCompatViewInflater {
    public <init>();
}

# Native callbacks are reached through JNI rather than Java references.
-keep class app.mpvnova.player.MPVLib {
    *;
}
