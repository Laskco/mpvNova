# AppCompat instantiates this class by its name in the theme.
-keep class app.mpvnova.player.OutlinedAppCompatViewInflater {
    public <init>();
}

# RecyclerView instantiates this LayoutManager by name from XML layouts.
-keep class app.mpvnova.player.TvWeightedLinearLayoutManager {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# Native callbacks are reached through JNI rather than Java references.
-keep class app.mpvnova.player.MPVLib {
    *;
}
