package app.mpvnova.player

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Verify aliases returned by search instead of accepting a popular but unrelated result. */
internal class TmdbCandidateMatching(
    private val query: TmdbTitleQuery,
    private val type: String,
    private val token: String,
    private val request: (String, String) -> JSONObject,
    private val report: (String) -> Unit,
) {
    @Suppress("ReturnCount") // Ambiguity and incomplete alias verification must retain local metadata.
    fun select(candidates: List<JSONObject>): JSONObject? {
        val nameKey = if (query.isEpisode) "name" else "title"
        val dateKey = if (query.isEpisode) "first_air_date" else "release_date"
        val eligible = candidates.filter {
            query.year == null || tmdbDateYear(it.optString(dateKey)) == query.year
        }
        if (eligible.isEmpty()) {
            if (candidates.isNotEmpty()) report("no result with the supplied year")
            return null
        }
        val name = normalizedTmdbTitle(query.title)
        val exact = eligible.filter { candidate ->
            listOfNotNull(candidate.safeTitle(nameKey), candidate.safeTitle("original_$nameKey"))
                .any { normalizedTmdbTitle(it) == name }
        }
        if (exact.size > 1) {
            report("ambiguous title; keeping local title")
            return null
        }
        if (exact.size == 1) return exact.single()
        if (eligible.size > MAX_ALIAS_CANDIDATES) {
            report("too many candidates to verify aliases")
            return null
        }
        report("incomplete alias response")
        val aliases = aliasMatches(eligible, name, nameKey) ?: return null
        report("no verified title match")
        if (aliases.size > 1) report("ambiguous alias; keeping local title")
        return aliases.singleOrNull()
    }

    private fun aliasMatches(eligible: List<JSONObject>, name: String, nameKey: String): List<JSONObject>? =
        eligible.filter { candidate ->
            val id = candidate.strictInt("id") ?: return null
            val details = request("$type/$id?append_to_response=alternative_titles,translations&language=en-US", token)
            if (details.strictInt("id") != id) return null
            val alternatives = details.optJSONObject("alternative_titles")
                ?.optJSONArray(if (query.isEpisode) "results" else "titles") ?: return null
            val translations = details.optJSONObject("translations")?.optJSONArray("translations") ?: return null
            val names = (0 until alternatives.length()).mapNotNull {
                val alternative = alternatives.optJSONObject(it) ?: return null
                if (alternative.opt("title") !is String) return null
                alternative.safeTitle("title")
            } + (0 until translations.length()).mapNotNull {
                val translated = translations.optJSONObject(it)?.optJSONObject("data") ?: return null
                if (translated.opt(nameKey) !is String) return null
                translated.safeTitle(nameKey)
            }
            names.any { normalizedTmdbTitle(it) == name }
        }

    companion object {
        private const val MAX_ALIAS_CANDIDATES = 4
    }
}

private val LATIN_DIACRITICS = Regex("[\\u0300-\\u036f]")
private val TITLE_FORMATTING = Regex("[^\\p{L}\\p{N}!?+%&]")
private val TMDB_DATE = Regex("""([0-9]{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])""")

internal fun normalizedTmdbTitle(value: String): String {
    // Preserve distinguishing !/!!, plus/percent signs, and Japanese voiced marks.
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD).replace(LATIN_DIACRITICS, "")
    return Normalizer.normalize(decomposed, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(TITLE_FORMATTING, "")
}

internal fun tmdbDateYear(value: String): Int? = TMDB_DATE.matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()
