@file:Suppress("TooManyFunctions") // Transport, validation, and publication share one bounded cache lifecycle.

package app.mpvnova.player

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val MAX_JSON_BYTES = 2 * 1024 * 1024
private const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024
private const val CONNECT_TIMEOUT_MS = 10000
private const val READ_TIMEOUT_MS = 15000
private const val CALL_TIMEOUT_MS = 60000L
private const val MAX_REDIRECTS = 3
private const val MAX_URL_LENGTH = 8192
private const val MAX_REQUEST_BYTES = 16384
private const val HTTPS_PORT = 443
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_RATE_LIMIT = 429
private const val MAX_CACHE_FILES = 4
private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
// Standard HTTP redirect response codes.
@Suppress("MagicNumber")
private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
private val API_HOSTS = setOf("api.opensubtitles.com", "vip-api.opensubtitles.com")
private val DOWNLOAD_HOSTS = API_HOSTS + setOf(
    "opensubtitles.com", "www.opensubtitles.com", "dl.opensubtitles.com", "dl.opensubtitles.org",
)
private val ERROR_CONTENT_TYPES = setOf(
    "text/html", "application/xhtml+xml", "application/json", "text/json", "application/xml", "text/xml",
)
private val PARTIAL_SUBTITLE_NAME = Regex("opensubtitles--?\\d+\\.part")
private val VTT_TIME_LINE = Regex("(?m)^\\s*(?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3}\\s+-->\\s+")
private val ASS_SCRIPT_INFO = Regex("(?im)^\\[Script Info]\\s*$")
private val ASS_EVENTS = Regex("(?im)^\\[Events]\\s*$")
private val ASS_DIALOGUE = Regex("(?im)^Dialogue:\\s*")
private val SRT_TIME_LINE = Regex("(?m)^\\s*\\d{1,3}:\\d{2}:\\d{2},\\d{3}\\s+-->\\s+\\d{1,3}:\\d{2}:\\d{2},\\d{3}")
private val MICRODVD_ENTRY = Regex("\\A\\{[0-9]{1,10}\\}\\{[0-9]{1,10}\\}[^\\r\\n]+(?:\\r?\\n|$)")
private val MPL_ENTRY = Regex("\\A\\[[0-9]{1,10}\\]\\[[0-9]{1,10}\\][^\\r\\n]+(?:\\r?\\n|$)")
private val DEADLINES = ScheduledThreadPoolExecutor(1) { task ->
    Thread(task, "opensubtitles-deadline").apply { isDaemon = true }
}.apply { removeOnCancelPolicy = true }
private val DISCONNECTS = ThreadPoolExecutor(
    0, 4, 30L, TimeUnit.SECONDS, SynchronousQueue(),
    { task -> Thread(task, "opensubtitles-cancel").apply { isDaemon = true } },
)
private val CACHE_MAINTENANCE = ScheduledThreadPoolExecutor(1) { task ->
    Thread(task, "opensubtitles-cache").apply { isDaemon = true }
}.apply { removeOnCancelPolicy = true }

/** Release only after playback unloads this file, or after a selection is abandoned.
 * Nonblocking. Released files may be evicted for capacity or after the 24-hour cache TTL.
 */
fun releaseOpenSubtitlesDownload(file: File) {
    OpenSubtitlesCache.release(file)
}

internal class OpenSubtitlesCacheSlot(val root: File) : Closeable {
    private val finished = AtomicBoolean(false)
    var partial: File? = null
    fun finish(): Boolean = finished.compareAndSet(false, true)
    override fun close() { OpenSubtitlesCache.close(this) }
}

internal object OpenSubtitlesCache {
    private data class Record(
        val file: File,
        val expires: Long,
        @Volatile var pinned: Boolean,
        val timer: ScheduledFuture<*>,
    )

    private val records = java.util.concurrent.ConcurrentHashMap<String, Record>()
    private val reservations = mutableMapOf<String, Int>()
    private val activePartials = mutableSetOf<String>()
    private val name = Regex("opensubtitles--?\\d+\\.(?:srt|ass|ssa|vtt|sub|mpl)")

    fun reserve(cacheDir: File): OpenSubtitlesCacheSlot {
        val root = cacheDir.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) fail(OpenSubtitlesFailureReason.CACHE)
        synchronized(this) {
            forgetMissing()
            root.listFiles { file -> name.matches(file.name) }?.forEach { file ->
                if (file.canonicalFile != file.absoluteFile) fail(OpenSubtitlesFailureReason.CACHE)
                if (!records.containsKey(file.path)) register(file, pinned = false)
            }
            sweep(root)
            val held = reservations[root.path] ?: 0
            val files = records.values.asSequence()
                .filter { it.file.parentFile == root }
                .map { it to it.file.lastModified() }
                .sortedBy { it.second }
                .map { it.first }
                .toList()
            var count = files.size + held
            for (record in files.filterNot { it.pinned }) {
                if (count < MAX_CACHE_FILES) break
                if (delete(record)) count--
            }
            if (count >= MAX_CACHE_FILES) fail(OpenSubtitlesFailureReason.CACHE)
            reservations[root.path] = held + 1
            return OpenSubtitlesCacheSlot(root)
        }
    }

    fun publish(slot: OpenSubtitlesCacheSlot, partial: File, destination: File, operation: OpenSubtitlesCall) {
        synchronized(this) {
            operation.checkActive()
            if (destination.exists() || !partial.renameTo(destination)) fail(OpenSubtitlesFailureReason.CACHE)
            operation.publishedFile = destination
            register(destination, pinned = true)
            close(slot)
            operation.checkActive()
        }
    }

    fun createPartial(slot: OpenSubtitlesCacheSlot): File = synchronized(this) {
        File.createTempFile("opensubtitles-", ".part", slot.root).also {
            slot.partial = it
            activePartials.add(it.path)
        }
    }

    fun close(slot: OpenSubtitlesCacheSlot) {
        synchronized(this) {
            if (!slot.finish()) return
            slot.partial?.let { activePartials.remove(it.path) }
            val remaining = (reservations[slot.root.path] ?: 1) - 1
            if (remaining == 0) reservations.remove(slot.root.path) else reservations[slot.root.path] = remaining
        }
    }

    fun release(file: File) {
        val record = records[file.absolutePath] ?: return
        record.pinned = false
        CACHE_MAINTENANCE.execute {
            runCatching {
                synchronized(this) { record.file.parentFile?.let(::sweep) }
            }
        }
    }

    fun forget(file: File) {
        synchronized(this) { records.remove(file.absolutePath)?.timer?.cancel(false) }
    }

    private fun register(file: File, pinned: Boolean) {
        val expires = file.lastModified() + CACHE_TTL_MS
        val timer = CACHE_MAINTENANCE.schedule({
            runCatching { synchronized(this) { file.parentFile?.let(::sweep) } }
        }, (expires - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        records[file.path] = Record(file, expires, pinned, timer)
    }

    private fun sweep(root: File) {
        val now = System.currentTimeMillis()
        records.values.filter { it.file.parentFile == root && !it.pinned && it.expires <= now }
            .forEach(::delete)
        // In-process partials are reserved; all other generated partials are restart orphans.
        root.listFiles { file -> PARTIAL_SUBTITLE_NAME.matches(file.name) }
            ?.filterNot { it.path in activePartials }
            ?.forEach { if (it.canonicalFile == it.absoluteFile) it.delete() }
    }

    private fun delete(record: Record): Boolean {
        if (record.pinned || record.file.canonicalFile != record.file.absoluteFile) return false
        val deleted = !record.file.exists() || record.file.delete()
        if (deleted) forget(record.file)
        return deleted
    }

    private fun forgetMissing() {
        records.values.filterNot { it.file.exists() }.forEach { forget(it.file) }
    }
}

internal class OpenSubtitlesCall(private val timeoutMillis: Long = CALL_TIMEOUT_MS) {
    private val state = AtomicInteger(RUNNING)
    private val connection = AtomicReference<HttpURLConnection?>()
    private val started = System.nanoTime()
    var publishedFile: File? = null

    fun startDeadline(): ScheduledFuture<*> =
        DEADLINES.schedule({ stop(TIMED_OUT) }, timeoutMillis, TimeUnit.MILLISECONDS)

    fun cancel() = stop(CANCELLED)

    private fun stop(newState: Int) {
        if (state.compareAndSet(RUNNING, newState)) {
            val resource = connection.getAndSet(null) ?: return
            try {
                DISCONNECTS.execute { runCatching { resource.disconnect() } }
            } catch (_: RejectedExecutionException) {
                // Bounded worker pool: read/connect timeouts still terminate the worker.
            }
        }
    }

    fun checkActive() {
        if (Thread.currentThread().isInterrupted) stop(CANCELLED)
        if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= timeoutMillis) stop(TIMED_OUT)
        when (state.get()) {
            CANCELLED -> fail(OpenSubtitlesFailureReason.CANCELLED)
            TIMED_OUT -> fail(OpenSubtitlesFailureReason.TIMEOUT)
        }
    }

    fun track(resource: HttpURLConnection) {
        connection.set(resource)
        try {
            checkActive()
        } catch (error: OpenSubtitlesException) {
            connection.compareAndSet(resource, null)
            resource.disconnect()
            throw error
        }
    }

    fun release(resource: HttpURLConnection) {
        connection.compareAndSet(resource, null)
        resource.disconnect()
    }

    fun complete() {
        checkActive()
        if (!state.compareAndSet(RUNNING, COMPLETED)) checkActive()
    }

    fun cleanup() {
        if (state.get() != COMPLETED) publishedFile?.let { file ->
            runCatching { file.delete() }
            OpenSubtitlesCache.forget(file)
        }
    }

    private companion object {
        const val RUNNING = 0
        const val COMPLETED = 1
        const val CANCELLED = 2
        const val TIMED_OUT = 3
    }
}

internal fun openSubtitlesApiHost(value: String): String {
    if (value.any { it.isWhitespace() }) fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    val candidate = if (value.contains("://")) value else "https://$value"
    val uri = safeHttpsUri(candidate, API_HOSTS)
    if (uri.rawQuery != null || uri.rawPath !in listOf("", "/", "/api/v1", "/api/v1/"))
        fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    return uri.host.lowercase(Locale.ROOT)
}

private fun safeHttpsUri(value: String, hosts: Set<String>): URI {
    if (value.length > MAX_URL_LENGTH || value.any { it < ' ' || it == '\u007F' })
        fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    val uri = try { URI(value) } catch (_: Exception) { fail(OpenSubtitlesFailureReason.UNSAFE_HOST) }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.rawFragment != null)
        fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    if (uri.port !in listOf(-1, HTTPS_PORT) || uri.host?.lowercase(Locale.ROOT) !in hosts)
        fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    return uri
}

internal fun openSubtitlesHttpFailure(status: Int): OpenSubtitlesException = OpenSubtitlesException(
    when (status) {
        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
            OpenSubtitlesFailureReason.AUTHENTICATION
        HttpURLConnection.HTTP_NOT_ACCEPTABLE -> OpenSubtitlesFailureReason.QUOTA
        HTTP_RATE_LIMIT -> OpenSubtitlesFailureReason.RATE_LIMIT
        else -> OpenSubtitlesFailureReason.HTTP
    }, status,
)

internal fun openSubtitlesHttpSuccess(status: Int): Boolean = status in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX

private fun redirectTarget(connection: HttpURLConnection, uri: URI, hosts: Set<String>): URI {
    val location = connection.getHeaderField("Location") ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
    if (location.length > MAX_URL_LENGTH) fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
    return try { safeHttpsUri(uri.resolve(location).toString(), hosts) }
        catch (_: IllegalArgumentException) { fail(OpenSubtitlesFailureReason.UNSAFE_HOST) }
}

/** Injection is internal and used by local fixtures; URL/host checks always run first. */
internal class OpenSubtitlesHttp(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val userAgent: String = "mpvNova v${BuildConfig.VERSION_NAME}",
) {
    fun api(
        operation: OpenSubtitlesCall,
        url: String,
        method: String,
        credentials: OpenSubtitlesCredentials,
        body: String?,
    ): String {
        val origin = safeHttpsUri(url, API_HOSTS)
        var uri = origin
        var redirects = 0
        while (true) {
            val connection = connection(operation, uri, method)
            try {
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Api-Key", credentials.apiKey)
                if (credentials.token.isNotEmpty())
                    connection.setRequestProperty("Authorization", "Bearer ${credentials.token}")
                writeApiBody(operation, connection, body)
                operation.checkActive()
                val status = connection.responseCode
                // GET canonicalization is supported only on this exact origin and endpoint.
                // Never replay a login body or transfer credentials to another host.
                if (method == "GET" && status in REDIRECT_CODES) {
                    if (redirects++ >= MAX_REDIRECTS) fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
                    val target = redirectTarget(connection, uri, API_HOSTS)
                    if (!target.host.equals(origin.host, ignoreCase = true) || target.rawPath != origin.rawPath)
                        fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
                    uri = target
                    continue
                }
                if (!openSubtitlesHttpSuccess(status)) throw openSubtitlesHttpFailure(status)
                requireIdentity(connection)
                val type = connection.getHeaderField("Content-Type")
                    ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                if (type != null && type != "application/json" && !type.endsWith("+json"))
                    fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
                val output = ByteArrayOutputStream()
                connection.inputStream.use { copy(operation, connection, it, output, MAX_JSON_BYTES) }
                return decode(ByteBuffer.wrap(output.toByteArray()), Charsets.UTF_8)
            } finally {
                operation.release(connection)
            }
        }
    }

    fun download(operation: OpenSubtitlesCall, link: String, slot: OpenSubtitlesCacheSlot, fileName: String): File {
        // Validate before touching the filesystem or making a request.
        var uri: URI? = atOpenSubtitlesStage(OpenSubtitlesFailureStage.DOWNLOAD_ADDRESS) {
            safeHttpsUri(link, DOWNLOAD_HOSTS)
        }
        val root = slot.root
        val partial = OpenSubtitlesCache.createPartial(slot)
        try {
            var requests = 0
            while (uri != null) {
                operation.checkActive()
                if (requests++ > MAX_REDIRECTS) fail(OpenSubtitlesFailureReason.UNSAFE_HOST)
                uri = atOpenSubtitlesStage(OpenSubtitlesFailureStage.DOWNLOAD_TRANSFER) {
                    downloadRequest(operation, checkNotNull(uri), partial)
                }
            }
            operation.checkActive()
            val extension = atOpenSubtitlesStage(OpenSubtitlesFailureStage.SUBTITLE_VALIDATION) {
                subtitleExtension(operation, partial, fileName)
            }
            val destination = File(root, "${partial.nameWithoutExtension}.$extension")
            OpenSubtitlesCache.publish(slot, partial, destination, operation)
            return destination
        } finally {
            partial.delete()
        }
    }

    private fun downloadRequest(operation: OpenSubtitlesCall, uri: URI, partial: File): URI? {
        val connection = connection(operation, uri, "GET")
        try {
            connection.setRequestProperty("Accept", "*/*")
            // No Api-Key, Authorization, Cookie, or Referer is copied from API calls.
            val status = connection.responseCode
            if (status in REDIRECT_CODES) return redirectTarget(connection, uri, DOWNLOAD_HOSTS)
            if (status != HttpURLConnection.HTTP_OK) throw openSubtitlesHttpFailure(status)
            requireIdentity(connection)
            rejectErrorContentType(connection)
            FileOutputStream(partial).use { output ->
                connection.inputStream.use { input -> copy(operation, connection, input, output, MAX_SUBTITLE_BYTES) }
                output.fd.sync()
            }
            return null
        } finally {
            operation.release(connection)
        }
    }

    private fun connection(operation: OpenSubtitlesCall, uri: URI, method: String): HttpURLConnection {
        operation.checkActive()
        val connection = open(uri.toURL())
        operation.track(connection)
        var configured = false
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Encoding", "identity")
            configured = true
            return connection
        } finally {
            if (!configured) operation.release(connection)
        }
    }
}

private fun writeApiBody(operation: OpenSubtitlesCall, connection: HttpURLConnection, body: String?) {
    if (body == null) return
    val bytes = body.toByteArray(Charsets.UTF_8)
    if (bytes.size > MAX_REQUEST_BYTES) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setFixedLengthStreamingMode(bytes.size)
    operation.checkActive()
    connection.outputStream.use { it.write(bytes) }
}

private fun requireIdentity(connection: HttpURLConnection) {
    val encoding = connection.getHeaderField("Content-Encoding")
    if (encoding != null && !encoding.trim().equals("identity", ignoreCase = true))
        fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
}

private fun rejectErrorContentType(connection: HttpURLConnection) {
    val type = connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return
    if (type in ERROR_CONTENT_TYPES || type.endsWith("+json"))
        fail(OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE)
}

private fun copy(
    operation: OpenSubtitlesCall,
    connection: HttpURLConnection,
    input: InputStream,
    output: OutputStream,
    limit: Int,
) {
    val header = connection.getHeaderField("Content-Length")
    val expected = header?.let {
        it.toLongOrNull()?.takeIf { value -> value >= 0L && it.all { character -> character in '0'..'9' } }
            ?: fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
    }
    if (expected != null && expected > limit) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
    var total = 0
    val bytes = ByteArray(8192)
    while (true) {
        operation.checkActive()
        val count = input.read(bytes, 0, minOf(bytes.size, limit - total + 1))
        operation.checkActive()
        if (count < 0) break
        total += count
        if (total > limit) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
        if (expected != null && total > expected) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
        output.write(bytes, 0, count)
    }
    if (expected != null && total.toLong() != expected) fail(OpenSubtitlesFailureReason.INVALID_RESPONSE)
}

private fun decode(bytes: ByteBuffer, charset: java.nio.charset.Charset): String = try {
    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(bytes).toString()
} catch (_: java.nio.charset.CharacterCodingException) { fail(OpenSubtitlesFailureReason.INVALID_RESPONSE) }

private class SubtitleTextBuffer : ByteArrayOutputStream() {
    fun byteRange(): ByteBuffer = ByteBuffer.wrap(buf, 0, count)
}

private fun readSubtitleText(operation: OpenSubtitlesCall, file: File): String {
    val bytes = file.inputStream().use { input ->
        val output = SubtitleTextBuffer()
        val buffer = ByteArray(8192)
        while (true) {
            operation.checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_SUBTITLE_BYTES) fail(OpenSubtitlesFailureReason.SIZE_LIMIT)
            output.write(buffer, 0, count)
        }
        output.byteRange()
    }
    val charset = when {
        bytes.remaining() >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
        bytes.remaining() >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }
    return decode(bytes, charset).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
}

private fun subtitleExtension(operation: OpenSubtitlesCall, file: File, fileName: String): String {
    val text = readSubtitleText(operation, file)
    operation.checkActive()
    if (text.isEmpty() || text.any { it < ' ' && it !in "\t\r\n" })
        fail(OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE)
    val legacyExtension = legacySubtitleExtension(text)
    if (legacyExtension != null) return legacyExtension
    if (text.startsWith("<") || text.startsWith("{"))
        fail(OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE)
    if (text.startsWith("[") && !text.startsWith("[Script Info]", ignoreCase = true))
        fail(OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE)
    val extension = when {
        isVttSubtitle(text) -> "vtt"
        isAssSubtitle(text) -> {
                if (fileName.substringAfterLast('.', "").equals("ssa", ignoreCase = true)) "ssa" else "ass"
            }
        isSrtSubtitle(text) -> "srt"
        else -> fail(OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE)
    }
    operation.checkActive()
    return extension
}

private fun isVttSubtitle(text: String): Boolean = text.startsWith("WEBVTT") &&
    VTT_TIME_LINE.containsMatchIn(text)

private fun isAssSubtitle(text: String): Boolean =
    ASS_SCRIPT_INFO.containsMatchIn(text) && ASS_EVENTS.containsMatchIn(text) && ASS_DIALOGUE.containsMatchIn(text)

private fun isSrtSubtitle(text: String): Boolean =
    SRT_TIME_LINE.containsMatchIn(text)

private fun legacySubtitleExtension(text: String): String? = when {
    MICRODVD_ENTRY.containsMatchIn(text) -> "sub"
    MPL_ENTRY.containsMatchIn(text) -> "mpl"
    else -> null
}
