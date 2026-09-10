package app.mpvnova.player

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class TmdbTitleQuery(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
) {
    val isEpisode: Boolean get() = season != null && episode != null

    companion object {
        fun from(local: PlayerTitlePresentation, filename: String? = null): TmdbTitleQuery? =
            TmdbQueryTitle.from(local, filename)
    }
}

internal data class TmdbTitleMatch(val title: String, val episodeTitle: String?)

internal class TmdbTitleLookup(private val request: (String, String) -> JSONObject = ::requestTmdbJson) {
    @Suppress("ReturnCount") // Each guard preserves the local title on an incomplete response.
    fun lookup(query: TmdbTitleQuery, token: String, report: (String) -> Unit = {}): TmdbTitleMatch? {
        report("no verified title match")
        val type = if (query.isEpisode) "tv" else "movie"
        val yearParameter = if (query.isEpisode) "first_air_date_year" else "year"
        val encoded = URLEncoder.encode(query.title, "UTF-8")
        val yearFilter = query.year?.let { "&$yearParameter=$it" }.orEmpty()
        val path = "search/$type?query=$encoded&include_adult=false&language=en-US$yearFilter"
        val candidates = searchCandidates(path, token, report) ?: return null
        val match = TmdbCandidateMatching(query, type, token, request, report).select(candidates) ?: return null
        val id = match.strictInt("id")?.takeIf { it > 0 } ?: return null
        val titleKey = if (query.isEpisode) "name" else "title"
        val title = match.safeTitle(titleKey) ?: return null
        if (!query.isEpisode) {
            report("matched movie")
            return TmdbTitleMatch(title, null)
        }
        report("verified series; episode unavailable")
        val details = episodeDetails(id, query, token) ?: return null
        if (details.strictInt("season_number") != query.season ||
            details.strictInt("episode_number") != query.episode
        ) return null
        return details.safeTitle("name")?.let {
            report("matched series and episode")
            TmdbTitleMatch(title, it)
        }
    }

    private fun episodeDetails(id: Int, query: TmdbTitleQuery, token: String): JSONObject? = try {
        request("tv/$id/season/${query.season}/episode/${query.episode}?language=en-US", token)
    } catch (error: TmdbHttpException) {
        if (error.status == HttpURLConnection.HTTP_NOT_FOUND) null else throw error
    }

    @Suppress("ReturnCount") // Only complete, bounded search results can establish uniqueness.
    private fun searchCandidates(path: String, token: String, report: (String) -> Unit): List<JSONObject>? {
        report("incomplete search response")
        val first = request(path, token)
        val pages = first.strictInt("total_pages") ?: return null
        val total = first.strictInt("total_results")?.takeIf { it >= 0 } ?: return null
        if (pages > MAX_SEARCH_PAGES || pages < 0) {
            report("search too broad; keeping local title")
            return null
        }
        if (pages == 0 && total != 0) return null
        val candidates = mutableListOf<JSONObject>()
        for (page in 1..maxOf(pages, 1)) {
            val response = if (page == 1) first else request("$path&page=$page", token)
            if (!hasSearchEnvelope(response, page, pages, total)) return null
            candidates += pageCandidates(response) ?: return null
        }
        val unique = candidates.distinctBy { it.strictInt("id") }
        if (candidates.size != total || unique.size != total) return null
        report(if (candidates.isEmpty()) "search returned no results" else "no verified title match")
        return unique
    }

    private fun hasSearchEnvelope(response: JSONObject, page: Int, pages: Int, total: Int): Boolean =
        response.strictInt("page") == page && response.strictInt("total_pages") == pages &&
            response.strictInt("total_results") == total

    @Suppress("ReturnCount") // Malformed rows make the entire candidate set indeterminate.
    private fun pageCandidates(response: JSONObject): List<JSONObject>? {
        val results = response.optJSONArray("results") ?: return null
        return (0 until results.length()).map { index ->
            val result = results.optJSONObject(index) ?: return null
            result.takeIf { (it.strictInt("id") ?: 0) > 0 } ?: return null
        }
    }

    companion object {
        private const val MAX_SEARCH_PAGES = 2
    }
}

private const val MAX_TMDB_TITLE_LENGTH = 512

internal fun JSONObject.safeTitle(key: String): String? = (opt(key) as? String)?.trim()?.takeIf {
    it.length in 1..MAX_TMDB_TITLE_LENGTH && TmdbQueryTitle.hasSearchableText(it)
}

internal fun JSONObject.strictInt(key: String): Int? {
    val value = opt(key)
    return when {
        value is Int -> value
        value is Long && value in Int.MIN_VALUE..Int.MAX_VALUE -> value.toInt()
        else -> null
    }
}

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
