package app.mpvnova.player

internal fun rememberedSeriesAspectRatio(enabled: Boolean, stored: String?, choices: List<String>): String? =
    if (enabled) stored?.takeIf { it in choices } ?: "-1" else null
