package app.mpvnova.player

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal data class VerifiedTmdbCandidate(val item: JSONObject, val episode: JSONObject? = null)

/** Verify aliases returned by search instead of accepting a popular but unrelated result. */
internal class TmdbCandidateMatching(
    private val query: TmdbTitleQuery,
    private val type: String,
    private val token: String,
    private val request: (String, String) -> JSONObject,
    private val report: (String) -> Unit,
) {
    @Suppress("ReturnCount") // Ambiguity and incomplete alias verification must retain local metadata.
    fun select(candidates: List<JSONObject>): VerifiedTmdbCandidate? {
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
            return distinguishEpisodes(exact)
        }
        if (exact.size == 1) return VerifiedTmdbCandidate(exact.single())
        if (eligible.size > MAX_ALIAS_CANDIDATES) {
            report("too many candidates to verify aliases")
            return null
        }
        report("incomplete alias response")
        val aliases = aliasMatches(eligible, name, nameKey) ?: return null
        report("no verified title match")
        if (aliases.size > 1) {
            report("ambiguous alias; keeping local title")
            return distinguishEpisodes(aliases)
        }
        return aliases.singleOrNull()?.let(::VerifiedTmdbCandidate)
    }

    @Suppress("ReturnCount") // Missing or conflicting episode evidence must leave the series ambiguous.
    private fun distinguishEpisodes(candidates: List<JSONObject>): VerifiedTmdbCandidate? {
        val episodeTitle = query.episodeTitle?.let(::normalizedTmdbTitle) ?: return null
        if (!query.isEpisode || candidates.size > MAX_ALIAS_CANDIDATES) return null
        val matches = candidates.mapNotNull { candidate ->
            val id = candidate.strictInt("id") ?: return null
            val episode = try {
                request("tv/$id/season/${query.season}/episode/${query.episode}?language=en-US", token)
            } catch (error: TmdbHttpException) {
                if (error.status == java.net.HttpURLConnection.HTTP_NOT_FOUND) return@mapNotNull null
                throw error
            }
            if (episode.strictInt("season_number") != query.season ||
                episode.strictInt("episode_number") != query.episode) return null
            val name = episode.safeTitle("name") ?: return null
            if (normalizedTmdbTitle(name) == episodeTitle) VerifiedTmdbCandidate(candidate, episode) else null
        }
        return matches.singleOrNull()
    }

    private fun aliasMatches(eligible: List<JSONObject>, name: String, nameKey: String): List<JSONObject>? =
        eligible.filter { candidate ->
            val id = candidate.strictInt("id") ?: return null
            val details = request("$type/$id?append_to_response=alternative_titles,translations&language=en-US", token)
            if (details.strictInt("id") != id) return null
            val alternatives = details.optJSONObject("alternative_titles")
                ?.optJSONArray(if (query.isEpisode) "results" else "titles") ?: return null
            val translations = details.optJSONObject("translations")?.optJSONArray("translations") ?: return null
            val foundAlternative = alternatives.hasMatchingTitle("title", name) { it } ?: return null
            translations.hasMatchingTitle(nameKey, name, foundAlternative) { it.optJSONObject("data") } ?: return null
        }

    companion object {
        private const val MAX_ALIAS_CANDIDATES = 4
    }
}

private inline fun JSONArray.hasMatchingTitle(
    key: String, name: String, alreadyMatched: Boolean = false, select: (JSONObject) -> JSONObject?,
): Boolean? {
    var matched = alreadyMatched
    for (index in 0 until length()) {
        val entry = optJSONObject(index)?.let(select)?.takeIf { it.opt(key) is String } ?: return null
        if (!matched) matched = entry.safeTitle(key)?.let(::normalizedTmdbTitle) == name
    }
    return matched
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
