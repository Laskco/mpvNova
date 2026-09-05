@file:Suppress("TooManyFunctions") // Copy publication, ownership, and cleanup form one cache lifecycle.

package app.mpvnova.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val SUBTITLE_NAME_UNSAFE = Regex("[^A-Za-z0-9._-]")
private const val MAX_SUBTITLE_BYTES = 32L * 1024L * 1024L
private const val MAX_SUBTITLE_REQUEST_BYTES = 128L * 1024L * 1024L
private const val SUBTITLE_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
private const val MAX_ORPHAN_CACHES_PER_SWEEP = 32
private val SUBTITLE_CACHE_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val subtitleCacheOwners = mutableMapOf<String, WeakReference<Any>>()
private val SUBTITLE_CLEANUP_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "external-subtitle-cleanup")
}
// Provider cancellation and close may themselves block; neither may occupy the UI or cleanup worker.
private val SUBTITLE_CANCEL_EXECUTOR = ThreadPoolExecutor(
    0, 4, 60L, TimeUnit.SECONDS, SynchronousQueue(),
    { runnable -> Thread(runnable, "external-subtitle-cancel") },
)

internal class ExternalSubtitleCopyRequest {
    private val cancelled = AtomicBoolean(false)
    private val activeResource = AtomicReference<Closeable?>()
    private val cacheDirectory = AtomicReference<File?>()
    private val completedSubtitles = linkedMapOf<Int, Uri>()
    private var retainedDirectory: File? = null
    private val ioCancellationStarted = AtomicBoolean(false)
    val cancellationSignal = CancellationSignal()
    var future: Future<*>? = null

    fun cancel() {
        val directory = synchronized(this) {
            cancelled.set(true)
            completedSubtitles.clear()
            cacheDirectory.getAndSet(null)
        }
        cancelIo()
        directory?.let(::discardExternalSubtitleCache)
    }

    fun takeCompletedSubtitles(): PreparedExternalSubtitles {
        val result = synchronized(this) {
            cancelled.set(true)
            val directory = cacheDirectory.getAndSet(null)
            val subtitles = completedSubtitles.toMap()
            completedSubtitles.clear()
            retainedDirectory = directory.takeIf { subtitles.isNotEmpty() }
            if (retainedDirectory == null) directory?.let(::discardExternalSubtitleCache)
            PreparedExternalSubtitles(retainedDirectory, subtitles)
        }
        cancelIo()
        return result
    }

    private fun cancelIo() {
        if (ioCancellationStarted.compareAndSet(false, true)) {
            future?.cancel(true)
            activeResource.getAndSet(null)?.let { resource -> cancelProviderIo { resource.close() } }
            cancelProviderIo { cancellationSignal.cancel() }
        }
    }

    fun trackDirectory(directory: File) = synchronized(this) {
        checkActive()
        cacheDirectory.set(directory)
        protectExternalSubtitleCache(directory, this)
    }

    fun publishSubtitle(index: Int, uri: Uri) = synchronized(this) {
        checkActive()
        completedSubtitles[index] = uri
    }

    fun workerFinished(directory: File) {
        val discard = synchronized(this) { cancelled.get() && retainedDirectory != directory }
        if (discard) discardExternalSubtitleCache(directory)
    }

    fun <T : Closeable, R> withActiveResource(resource: T, action: (T) -> R): R = resource.use {
        activeResource.set(resource)
        try {
            checkActive()
            action(resource)
        } finally {
            activeResource.compareAndSet(resource, null)
        }
    }

    fun checkActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted)
            throw CancellationException("Subtitle request cancelled")
    }
}

internal data class PreparedExternalSubtitles(val directory: File?, val copied: Map<Int, Uri>)

// Construct the launch snapshot only after publication is frozen; failed providers never reach
// MPVActivity's synchronous content-URI resolver. Parallel track metadata keeps the same indices.
@Suppress("DEPRECATION")
internal fun Intent.applyPreparedSubtitles(source: Intent, prepared: PreparedExternalSubtitles): Int {
    val subs = source.getParcelableArrayExtra("subs")?.filterIsInstance<Uri>().orEmpty()
    val indices = subs.indices.filter { subs[it].scheme != "content" || it in prepared.copied }
    val rewritten = indices.associate { subs[it] to (prepared.copied[it] ?: subs[it]) }
    putExtra("subs", indices.map { prepared.copied[it] ?: subs[it] }.toTypedArray())
    source.getParcelableArrayExtra("subs.enable")?.filterIsInstance<Uri>()?.let { selected ->
        putExtra("subs.enable", selected.mapNotNull { rewritten[it] }.toTypedArray())
    }
    for (key in listOf("subs.name", "subs.filename")) {
        source.getStringArrayExtra(key)?.let { labels ->
            putExtra(key, indices.map { labels.getOrElse(it) { "" } }.toTypedArray())
        }
    }
    return subs.size - indices.size
}

@Suppress("DEPRECATION")
internal fun Intent.hasContentSubtitles(): Boolean =
    getParcelableArrayExtra("subs")?.filterIsInstance<Uri>()?.any { it.scheme == "content" } == true

// Nuvio and the like hand subtitles over as content:// URIs from their own cache, which
// mpv can't open. Copy them locally while we still hold the read grant and relay file URIs.
@Suppress("DEPRECATION")
internal fun Context.materializeContentSubtitles(
    source: Intent,
    request: ExternalSubtitleCopyRequest,
) {
    val subs = source.getParcelableArrayExtra("subs")?.filterIsInstance<Uri>().orEmpty()
    if (subs.none { it.scheme == "content" }) return
    request.checkActive()
    val directory = File(cacheDir.canonicalFile, "external_subs/${UUID.randomUUID()}")
    try {
        request.trackDirectory(directory)
        if (!directory.mkdirs()) throw IOException("Could not create subtitle cache")
        val budget = SubtitleCopyBudget()
        subs.forEachIndexed { index, uri ->
            request.checkActive()
            if (uri.scheme == "content") {
                copyContentSubtitle(index, uri, directory, budget, request)?.let {
                    request.publishSubtitle(index, it)
                }
            }
        }
    } finally {
        request.workerFinished(directory)
    }
}

private class SubtitleCopyBudget {
    var remaining = MAX_SUBTITLE_REQUEST_BYTES
}

private fun Context.copyContentSubtitle(
    index: Int,
    uri: Uri,
    directory: File,
    budget: SubtitleCopyBudget,
    request: ExternalSubtitleCopyRequest,
): Uri? {
    if (budget.remaining <= 0L) return null
    val dest = File(directory, "${index}_${subtitleCacheName(uri)}")
    var completed = false
    return try {
        runCatching {
            copySubtitleToFile(uri, dest, budget, request)
            request.checkActive()
            Uri.fromFile(dest).also { completed = true }
        }.getOrElse { error ->
            request.checkActive()
            Log.w("ExternalSubtitleCache", "Could not cache external subtitle", error)
            null
        }
    } finally {
        if (!completed) dest.delete()
    }
}

private fun Context.copySubtitleToFile(
    uri: Uri,
    dest: File,
    budget: SubtitleCopyBudget,
    request: ExternalSubtitleCopyRequest,
) {
    request.checkActive()
    val descriptor = contentResolver.openAssetFileDescriptor(uri, "r", request.cancellationSignal)
        ?: throw IOException("Could not open external subtitle")
    request.withActiveResource(descriptor) {
        request.withActiveResource(it.createInputStream()) { input ->
            dest.outputStream().use { output -> copyBoundedSubtitle(input, output, budget, request) }
        }
    }
}

private fun copyBoundedSubtitle(
    input: InputStream,
    output: OutputStream,
    budget: SubtitleCopyBudget,
    request: ExternalSubtitleCopyRequest,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var fileBytes = 0L
    while (true) {
        request.checkActive()
        val allowance = minOf(buffer.size.toLong(), MAX_SUBTITLE_BYTES - fileBytes + 1L, budget.remaining + 1L)
        val read = input.read(buffer, 0, allowance.toInt())
        if (read < 0) break
        fileBytes += read
        val withinBudget = read <= budget.remaining
        budget.remaining = (budget.remaining - read).coerceAtLeast(0L)
        if (fileBytes > MAX_SUBTITLE_BYTES || !withinBudget)
            throw IOException("External subtitle copy limit exceeded")
        request.checkActive()
        output.write(buffer, 0, read)
    }
}

private fun cancelProviderIo(action: () -> Unit) {
    try {
        SUBTITLE_CANCEL_EXECUTOR.execute {
            runCatching(action).onFailure { Log.w("ExternalSubtitleCache", "Provider cancellation failed", it) }
        }
    } catch (error: RejectedExecutionException) {
        Log.w("ExternalSubtitleCache", "Provider cancellation workers are busy", error)
    }
}

internal fun discardExternalSubtitleCache(directory: File) {
    SUBTITLE_CLEANUP_EXECUTOR.execute {
        synchronized(subtitleCacheOwners) {
            subtitleCacheOwners.remove(directory.subtitleCacheKey())
            deleteExternalSubtitleCache(directory)
        }
    }
}

internal fun deleteExternalSubtitleCache(directory: File) {
    runCatching {
        // Allow Android's cache-root aliases, but never follow a child-directory
        // symlink or recurse into nested content. The parent is our own cache root.
        val canonical = directory.canonicalFile
        if (canonical != File(directory.parentFile.canonicalFile, directory.name)) return@runCatching
        canonical.listFiles()?.forEach { it.delete() }
        if (canonical.exists() && !canonical.delete()) throw IOException("Could not delete subtitle cache")
    }.onFailure { Log.w("ExternalSubtitleCache", "Subtitle cache cleanup failed", it) }
}

internal fun protectExternalSubtitleCache(directory: File, owner: Any) {
    synchronized(subtitleCacheOwners) {
        subtitleCacheOwners[directory.subtitleCacheKey()] = WeakReference(owner)
    }
}

private fun File.subtitleCacheKey(): String = runCatching { canonicalPath }.getOrDefault(absolutePath)

internal fun Context.reclaimExternalSubtitleCaches() {
    val appContext = applicationContext
    SUBTITLE_CLEANUP_EXECUTOR.execute {
        runCatching {
            val root = File(appContext.cacheDir.canonicalFile, "external_subs")
            if (root.canonicalFile != root.absoluteFile) return@runCatching
            val cutoff = System.currentTimeMillis() - SUBTITLE_CACHE_TTL_MS
            var reclaimed = 0
            for (directory in root.listFiles().orEmpty()) {
                if (reclaimed >= MAX_ORPHAN_CACHES_PER_SWEEP) break
                if (directory.reclaimOrphanSubtitleCache(cutoff)) reclaimed++
            }
        }.onFailure { Log.w("ExternalSubtitleCache", "Subtitle orphan cleanup failed", it) }
    }
}

private fun File.reclaimOrphanSubtitleCache(cutoff: Long): Boolean {
    val eligible = SUBTITLE_CACHE_UUID.matches(name) && isDirectory && lastModified() < cutoff
    if (!eligible) return false
    // Registration and the final check/delete are atomic with respect to restored owners.
    return synchronized(subtitleCacheOwners) {
        val key = subtitleCacheKey()
        if (subtitleCacheOwners[key]?.get() != null) {
            false
        } else {
            subtitleCacheOwners.remove(key)
            deleteExternalSubtitleCache(this)
            true
        }
    }
}

private fun subtitleCacheName(uri: Uri): String {
    val raw = (uri.lastPathSegment?.substringAfterLast('/') ?: "").ifBlank { "subtitle" }
    val cleaned = raw.replace(SUBTITLE_NAME_UNSAFE, "_")
    return if (cleaned.contains('.')) cleaned else "$cleaned.srt"
}
