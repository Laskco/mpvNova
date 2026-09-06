package app.mpvnova.player

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

internal data class TmdbTitleQuery(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
) {
    val isEpisode: Boolean get() = season != null && episode != null

    companion object {
        private val YEAR = Regex("""\s+\((\d{4})\)$""")
        private const val MAX_QUERY_LENGTH = 256

        @Suppress("ReturnCount") // Reject incomplete identities before making any network request.
        fun from(local: PlayerTitlePresentation, filename: String? = null): TmdbTitleQuery? {
            val title = local.title.trim()
            val pathLike = title.contains('/') || title.contains('\\')
            if (title.length !in 2..MAX_QUERY_LENGTH || pathLike) {
                return null
            }
            val yearMatch = YEAR.find(title)
            val name = yearMatch?.let { title.substring(0, it.range.first).trim() } ?: title
            val season = local.season
            val episode = local.episode
            val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: filenameMovieYear(local, name, filename)
            // Absolute anime episode numbers are not necessarily TMDB season-relative numbers.
            if (season != null || episode != null) {
                val incomplete = season == null || episode == null
                if (incomplete || season < 0 || episode <= 0) return null
            } else if (year == null) {
                // Without a type hint, a bare title could be either a movie or a TV series.
                return null
            }
            return TmdbTitleQuery(name, year, season, episode).takeIf { name.any(Char::isLetter) }
        }

        private fun filenameMovieYear(local: PlayerTitlePresentation, name: String, filename: String?): Int? =
            if (local.season == null && local.episode == null) TmdbFilenameYear.matchingYear(name, filename) else null
    }
}

internal data class TmdbTitleMatch(val title: String, val episodeTitle: String?)

internal class TmdbTitleLookup(private val request: (String, String) -> JSONObject = ::requestTmdbJson) {
    @Suppress("ReturnCount") // Each guard preserves the local title on an incomplete response.
    fun lookup(query: TmdbTitleQuery, token: String): TmdbTitleMatch? {
        val type = if (query.isEpisode) "tv" else "movie"
        val yearParameter = if (query.isEpisode) "first_air_date_year" else "year"
        val encoded = URLEncoder.encode(query.title, "UTF-8")
        val yearFilter = query.year?.let { "&$yearParameter=$it" }.orEmpty()
        val search = request("search/$type?query=$encoded&include_adult=false&language=en-US$yearFilter", token)
        val match = selectMatch(query, search) ?: return null
        val id = match.optInt("id", -1).takeIf { it > 0 } ?: return null
        val titleKey = if (query.isEpisode) "name" else "title"
        val title = match.safeTitle(titleKey) ?: return null
        if (!query.isEpisode) return TmdbTitleMatch(title, null)
        val details = request("tv/$id/season/${query.season}/episode/${query.episode}?language=en-US", token)
        if (details.optInt("season_number", -1) != query.season ||
            details.optInt("episode_number", -1) != query.episode
        ) return null
        return details.safeTitle("name")?.let { TmdbTitleMatch(title, it) }
    }

    @Suppress("ReturnCount") // Incomplete or paginated searches cannot establish a unique match.
    private fun selectMatch(query: TmdbTitleQuery, search: JSONObject): JSONObject? {
        if (search.optInt("total_pages", 1) > 1) return null
        val results = search.optJSONArray("results") ?: return null
        val nameKey = if (query.isEpisode) "name" else "title"
        val dateKey = if (query.isEpisode) "first_air_date" else "release_date"
        val name = normalizedTmdbTitle(query.title)
        val matches = (0 until results.length()).mapNotNull(results::optJSONObject).filter { result ->
            val names = listOfNotNull(result.safeTitle(nameKey), result.safeTitle("original_$nameKey"))
            val sameName = names.any { normalizedTmdbTitle(it) == name }
            val sameYear = query.year == null ||
                result.optString(dateKey).take(4).toIntOrNull() == query.year
            sameName && sameYear
        }
        return matches.singleOrNull()
    }
}

private const val MAX_TMDB_TITLE_LENGTH = 512

private fun JSONObject.safeTitle(key: String): String? =
    (opt(key) as? String)?.trim()?.takeIf { it.length in 1..MAX_TMDB_TITLE_LENGTH && it.any(Char::isLetter) }

private fun normalizedTmdbTitle(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

private const val TMDB_TIMEOUT_MS = 6000
private const val TMDB_MAX_RESPONSE_BYTES = 512 * 1024

internal fun tmdbAuthenticatedPath(path: String, credential: String): String {
    if (!TmdbSettings.isApiKey(credential)) return path
    val separator = if ('?' in path) '&' else '?'
    return "$path${separator}api_key=$credential"
}

internal class TmdbHttpException(val status: Int) : IOException("TMDB request failed with HTTP $status")

internal fun requestTmdbJson(path: String, token: String): JSONObject {
    if (Thread.currentThread().isInterrupted) throw IOException("Lookup cancelled")
    val authenticatedPath = tmdbAuthenticatedPath(path, token)
    val connection = URL("https://api.themoviedb.org/3/$authenticatedPath").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TMDB_TIMEOUT_MS
        connection.readTimeout = TMDB_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        if (!TmdbSettings.isApiKey(token)) connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) throw TmdbHttpException(status)
        val bytes = connection.inputStream.use { it.readBytesBounded(TMDB_MAX_RESPONSE_BYTES) }
        return JSONObject(bytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw IOException("Lookup cancelled")
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("TMDB response too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
