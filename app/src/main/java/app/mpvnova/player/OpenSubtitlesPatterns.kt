package app.mpvnova.player

internal val OPEN_SUBTITLES_LANGUAGE_CODE = Regex("[a-z]{2,3}(?:-[a-z]{2,3})?")
internal val OPEN_SUBTITLES_PREFERENCE_LANGUAGE = Regex("[a-z]{2,3}(?:-[a-z]{2,4})?")
internal val OPEN_SUBTITLES_HASH = Regex("[0-9a-fA-F]{16}")
