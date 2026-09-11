package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.util.Locale
import java.util.MissingResourceException

internal const val PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES = "limit_preferred_language_subtitles"

internal fun SharedPreferences.migratePreferredSubtitleOptIn() = synchronized(this) {
    if (!getBoolean(PREF_SUBTITLE_OPT_IN_RESET, false)) {
        edit().putBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)
            .putBoolean(PREF_SUBTITLE_OPT_IN_RESET, true).apply()
    }
}

private const val PREF_SUBTITLE_OPT_IN_RESET = "preferred_subtitles_opt_in_reset_v1"

// Run once at file load, after restoring audio. Never fight a later manual subtitle selection.
@Suppress("ReturnCount") // Disabled policy and non-preferred audio keep the existing behavior.
internal fun MPVActivity.applyPreferredLanguageSubtitles(): Boolean {
    val prefs = getDefaultSharedPreferences(applicationContext)
    if (!prefs.getBoolean(PREF_LIMIT_PREFERRED_LANGUAGE_SUBTITLES, false)) return false
    val audioLanguage = listTrackMeta("audio").firstOrNull { it.mpvId == player.aid }?.lang.orEmpty()
    val candidates = limitedSubtitlesForPreferredAudio(
        audioLanguage,
        prefs.getString("default_audio_language", "eng").orEmpty(),
        prefs.getString("default_subtitle_language", "eng").orEmpty(),
        listTrackMeta("sub"),
        player.sid,
    ) ?: return false
    // Constrain mpv's later autoselection too; explicit manual selections still work.
    mpvSetPropertyString("file-local-options/subs-with-matching-audio", "forced")
    selectTrackForFile("sub", candidates.firstOrNull()?.mpvId ?: -1)
    return true
}

internal fun limitedSubtitlesForPreferredAudio(
    audioLanguage: String, preferredAudio: String, preferredSubtitles: String, tracks: List<TrackMeta>,
    currentSubtitle: Int? = null,
): List<TrackMeta>? {
    val audioLanguages = preferredAudio.split(',').map(String::trim).filter(String::isNotEmpty)
    if (audioLanguages.none { subtitleLanguageMatches(audioLanguage, it) }) return null
    val subtitleLanguages = preferredSubtitles.split(',').map(String::trim).filter(String::isNotEmpty)
    // Without a configured subtitle language, use the selected (understood) audio language.
    val languages = subtitleLanguages.ifEmpty { listOf(audioLanguage) }
    return tracks.filter { track ->
        val companionLanguage = track.lang.takeIf { subtitleLanguageMatches(it, audioLanguage) }.orEmpty()
        isLimitedSubtitleTrack(track.title, track.forced, companionLanguage) &&
            (languages.any { subtitleLanguageMatches(track.lang, it) } ||
                isLimitedSubtitleTrack(track.title, track.forced) ||
                (track.lang.isBlank() && track.mpvId == currentSubtitle))
    }.sortedBy { track ->
        languages.indexOfFirst { subtitleLanguageMatches(track.lang, it) }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}

internal fun subtitleLanguageMatches(first: String, second: String): Boolean =
    first.isNotBlank() && second.isNotBlank() && subtitleLanguageCode(first) == subtitleLanguageCode(second)

private fun subtitleLanguageCode(value: String): String {
    val code = value.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
    val language = SUBTITLE_LANGUAGE_ALIASES[code] ?: code
    return try {
        Locale.forLanguageTag(language).isO3Language.ifEmpty { language }
    } catch (_: MissingResourceException) {
        language
    }
}

private val SUBTITLE_LANGUAGE_ALIASES = mapOf(
    "english" to "eng", "japanese" to "jpn", "german" to "deu", "french" to "fra",
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
    "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
    "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
    "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)
