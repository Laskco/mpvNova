package app.mpvnova.player

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val MAX_USERNAME_LENGTH = 256
private const val MAX_PASSWORD_LENGTH = 4096
private const val MAX_QUERY_LENGTH = 512
private const val MAX_HEADER_LENGTH = 8192
private const val MAX_PAGE_RESULTS = 2000
private const val MAX_JSON_NESTING = 32
private const val JSON_BUDGET_CHECK_STEP = 8192

data class OpenSubtitlesCredentials(
    val apiKey: String,
    val token: String = "",
    val apiHost: String = "api.opensubtitles.com",
) {
    override fun toString(): String = "OpenSubtitlesCredentials(redacted)"
}

data class OpenSubtitlesQuery(
    val title: String,
    val language: String = "en",
    val season: Int? = null,
    val episode: Int? = null,
    val movieHash: String? = null,
)

data class OpenSubtitlesLanguage(val code: String, val name: String)
data class OpenSubtitlesResult(
    val fileId: Int,
    val fileName: String,
    val language: String,
    val release: String,
    val hearingImpaired: Boolean = false,
    val downloadCount: Int = 0,
    val hashMatched: Boolean = false,
)

data class OpenSubtitlesSearchPage(val results: List<OpenSubtitlesResult>, val page: Int, val totalPages: Int)
data class OpenSubtitlesSession(
    val credentials: OpenSubtitlesCredentials,
    val username: String,
    val allowedDownloads: Int? = null,
)

data class OpenSubtitlesDownload(val file: File, val remaining: Int? = null, val resetTime: String? = null)

enum class OpenSubtitlesFailureReason {
    INVALID_REQUEST, UNSAFE_HOST, AUTHENTICATION, QUOTA, RATE_LIMIT, HTTP,
    NETWORK, TIMEOUT, CANCELLED, BUSY, INVALID_RESPONSE, SIZE_LIMIT, UNSUPPORTED_SUBTITLE, CACHE,
}

enum class OpenSubtitlesFailureStage {
    DOWNLOAD_REQUEST, DOWNLOAD_METADATA, DOWNLOAD_ADDRESS, DOWNLOAD_TRANSFER, SUBTITLE_VALIDATION,
}

// Never retain a server body, URL, local path, credential, or underlying exception.
class OpenSubtitlesException(
    val reason: OpenSubtitlesFailureReason,
    val status: Int? = null,
    val stage: OpenSubtitlesFailureStage? = null,
) : IOException("OpenSubtitles request failed: ${reason.name}")

/** Blocking worker-thread API. One operation per client; simultaneous callers receive BUSY.
 * Cancellation belongs to the current operation only, and does not poison a later call.
 */
class OpenSubtitlesClient internal constructor(private val http: OpenSubtitlesHttp) {
    constructor() : this(OpenSubtitlesHttp())

    private val active = AtomicReference<OpenSubtitlesCall?>()

    fun cancel() {
        active.get()?.cancel()
    }

    fun verifyApiKey(apiKey: String) = call { operation ->
        // The languages endpoint is public and accepts invalid keys. Search is key-gated
        // and does not consume download quota; use a small, unlikely-to-match query.
        json(operation, OpenSubtitlesCredentials(apiKey), "GET", "subtitles?query=mpvnova+key+verification")
            .getJSONArray("data")
        Unit
    }

    fun login(apiKey: String, username: String, password: String): OpenSubtitlesSession = call { operation ->
        if (username.isBlank() || username.length > MAX_USERNAME_LENGTH)
            fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
        if (password.isEmpty() || password.length > MAX_PASSWORD_LENGTH)
            fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
        val response = json(operation, OpenSubtitlesCredentials(apiKey), "POST", "login",
            JSONObject().put("username", username).put("password", password))
        val token = response.requiredString("token")
        requireHeader(token)
        val host = openSubtitlesApiHost(response.requiredString("base_url"))
        OpenSubtitlesSession(
            OpenSubtitlesCredentials(apiKey, token, host), username,
            response.optJSONObject("user")?.optionalInt("allowed_downloads"),
        )
    }

    fun logout(credentials: OpenSubtitlesCredentials) = call { operation ->
        requireToken(credentials)
        json(operation, credentials, "DELETE", "logout", allowEmpty = true)
        Unit
    }

    fun languages(credentials: OpenSubtitlesCredentials): List<OpenSubtitlesLanguage> = call { operation ->
        val data = json(operation, credentials, "GET", "infos/languages").getJSONArray("data")
        (0 until data.length()).map { index ->
            operation.checkActive()
            val entry = data.getJSONObject(index)
            OpenSubtitlesLanguage(entry.requiredString("language_code"), entry.requiredString("language_name"))
        }
    }

    fun search(
        credentials: OpenSubtitlesCredentials,
        query: OpenSubtitlesQuery,
        page: Int = 1,
    ): OpenSubtitlesSearchPage = call { operation ->
        validateQuery(query, page)
        val parameters = sortedMapOf("languages" to query.language)
        // The service canonicalizes away the default page using a 301 redirect.
        if (page > 1) parameters["page"] = page.toString()
        if (query.title.isNotBlank()) parameters["query"] = query.title.trim()
        query.season?.let { parameters["season_number"] = it.toString() }
        query.episode?.let { parameters["episode_number"] = it.toString() }
        query.movieHash?.let { parameters["moviehash"] = it.lowercase(Locale.ROOT) }
        val path = "subtitles?" + parameters.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val response = json(operation, credentials, "GET", path)
        val returnedPage = response.requiredNonnegativeInt("page")
        val totalPages = response.requiredNonnegativeInt("total_pages")
        if (returnedPage != page) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        val results = response.getJSONArray("data").subtitleResults(operation)
        OpenSubtitlesSearchPage(results, returnedPage, totalPages)
    }

    fun download(
        credentials: OpenSubtitlesCredentials,
        result: OpenSubtitlesResult,
        cacheDir: File,
    ): OpenSubtitlesDownload = call { operation ->
        if (result.fileId <= 0) fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
        // Reserve capacity before consuming remote quota. Active playback files are never evicted.
        OpenSubtitlesCache.reserve(cacheDir).use { slot ->
            // No conversion, timeshift, force_download, or paid-action parameters.
            val response = atOpenSubtitlesStage(OpenSubtitlesFailureStage.DOWNLOAD_REQUEST) {
                json(operation, credentials, "POST", "download", JSONObject().put("file_id", result.fileId))
            }
            atOpenSubtitlesStage(OpenSubtitlesFailureStage.DOWNLOAD_METADATA) {
                val remaining = response.optionalInt("remaining", allowNegative = true)
                val link = response.optionalString("link")
                if (link.isNullOrBlank()) fail(
                    if (remaining != null && remaining <= 0) OpenSubtitlesFailureReason.QUOTA
                    else OpenSubtitlesFailureReason.INVALID_RESPONSE,
                )
                val file = http.download(operation, link, slot,
                    response.optionalString("file_name") ?: result.fileName)
                OpenSubtitlesDownload(file, remaining, response.optionalString("reset_time_utc")
                    ?: response.optionalString("reset_time"))
            }
        }
    }

    private fun json(
        operation: OpenSubtitlesCall,
        credentials: OpenSubtitlesCredentials,
        method: String,
        path: String,
        body: JSONObject? = null,
        allowEmpty: Boolean = false,
    ): JSONObject {
        requireHeader(credentials.apiKey)
        if (credentials.token.isNotEmpty()) requireHeader(credentials.token)
        val host = openSubtitlesApiHost(credentials.apiHost)
        if (host == "vip-api.opensubtitles.com") requireToken(credentials)
        val text = http.api(operation, "https://$host/api/v1/$path", method, credentials, body?.toString())
        val response = if (allowEmpty && text.isBlank()) JSONObject() else {
            requireJsonBudget(operation, text)
            val parser = JSONTokener(text)
            val value = parser.nextValue()
            if (value !is JSONObject || parser.nextClean() != '\u0000')
                fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
            value
        }
        if (response.has("status")) {
            val status = response.requiredNonnegativeInt("status")
            if (!openSubtitlesHttpSuccess(status)) throw openSubtitlesHttpFailure(status)
        }
        if (response.has("error") || response.has("errors")) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        return response
    }

    private fun <T> call(action: (OpenSubtitlesCall) -> T): T {
        val operation = OpenSubtitlesCall()
        if (!active.compareAndSet(null, operation)) fail(OpenSubtitlesFailureReason.BUSY)
        val deadline = operation.startDeadline()
        try {
            operation.checkActive()
            val result = action(operation)
            operation.complete()
            return result
        } catch (error: OpenSubtitlesException) {
            operation.checkActive()
            throw error
        } catch (_: JSONException) {
            operation.checkActive()
            fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        } catch (_: java.net.SocketTimeoutException) {
            operation.checkActive()
            fail(OpenSubtitlesFailureReason.TIMEOUT)
        } catch (_: IOException) {
            operation.checkActive()
            fail(OpenSubtitlesFailureReason.NETWORK)
        } catch (_: IllegalArgumentException) {
            operation.checkActive()
            fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        } catch (_: SecurityException) {
            operation.checkActive()
            fail(OpenSubtitlesFailureReason.CACHE)
        } finally {
            deadline.cancel(false)
            operation.cleanup()
            active.compareAndSet(operation, null)
        }
    }
}

internal fun fail(reason: OpenSubtitlesFailureReason): Nothing = throw OpenSubtitlesException(reason)

private fun requireHeader(value: String) {
    if (value.isEmpty() || value.length > MAX_HEADER_LENGTH || value.any { it !in '!'..'~' })
        fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
}

private fun validateQuery(query: OpenSubtitlesQuery, page: Int) {
    if (page < 1 || query.title.length > MAX_QUERY_LENGTH) fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
    if (query.title.isBlank() && query.movieHash == null) fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
    if (!OPEN_SUBTITLES_LANGUAGE_CODE.matches(query.language))
        fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
    if (query.season?.let { it < 0 } == true || query.episode?.let { it < 1 } == true)
        fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
    if (query.movieHash?.let { !OPEN_SUBTITLES_HASH.matches(it) } == true)
        fail(OpenSubtitlesFailureReason.INVALID_REQUEST)
}

// Android's parser accepts comments; reject them before they can hide or offset nesting.
private fun requireJsonBudget(operation: OpenSubtitlesCall, text: String) {
    var depth = 0
    var quote: Char? = null
    var escaped = false
    var previous: Char? = null
    for ((index, character) in text.withIndex()) {
        if (index % JSON_BUDGET_CHECK_STEP == 0) operation.checkActive()
        if (quote != null) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> quote = null
            }
            continue
        }
        when (character) {
            '"' -> {
                quote = character
            }
            '/', '#', '\'' -> fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        }
        depth = jsonStructureDepth(depth, character, previous)
        if (character !in " \t\r\n") previous = character
    }
    if (depth != 0 || quote != null) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
}

private fun jsonStructureDepth(depth: Int, character: Char, previous: Char?): Int {
    // A quote within an unquoted literal is not a string opener to Android's parser.
    if (character == '"' && previous != null && previous !in "{[:,")
        fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
    val updated = when (character) {
        '{', '[' -> depth + 1
        '}', ']' -> depth - 1
        else -> depth
    }
    if (updated < 0) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
    if (updated > MAX_JSON_NESTING) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
    return updated
}

private fun JSONArray.subtitleResults(operation: OpenSubtitlesCall): List<OpenSubtitlesResult> {
    val results = mutableListOf<OpenSubtitlesResult>()
    for (index in 0 until length()) {
        operation.checkActive()
        val attributes = getJSONObject(index).getJSONObject("attributes")
        val files = attributes.getJSONArray("files")
        for (fileIndex in 0 until files.length()) {
            val file = files.getJSONObject(fileIndex)
            val id = file.requiredNonnegativeInt("file_id")
            if (id == 0) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
            results.add(OpenSubtitlesResult(
                id, file.optionalString("file_name").orEmpty(), attributes.requiredString("language"),
                attributes.optionalString("release").orEmpty(), attributes.optBoolean("hearing_impaired"),
                attributes.optionalInt("download_count") ?: 0,
                attributes.optBoolean("moviehash_match"),
            ))
            if (results.size > MAX_PAGE_RESULTS) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
        }
    }
    return results
}

private fun requireToken(credentials: OpenSubtitlesCredentials) = requireHeader(credentials.token)

private fun JSONObject.requiredString(key: String): String = optionalString(key)?.takeIf { it.isNotBlank() }
    ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return (get(key) as? String) ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
}

private fun JSONObject.requiredNonnegativeInt(key: String): Int = optionalInt(key)
    ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)

private fun JSONObject.optionalInt(key: String, allowNegative: Boolean = false): Int? {
    if (!has(key) || isNull(key)) return null
    val value = get(key)
    val parsed = when (value) {
        is Number -> value.toString().toIntOrNull()
        is String -> value.toIntOrNull()
        else -> null
    }
    return parsed?.takeIf { allowNegative || it >= 0 } ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
}
