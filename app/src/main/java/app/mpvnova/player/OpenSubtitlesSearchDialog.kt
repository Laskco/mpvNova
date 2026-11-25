package app.mpvnova.player

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.text.DateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Constructor-only seam for local fixtures; production always uses the normal client. */
internal interface OpenSubtitlesSearchService {
    fun languages(credentials: OpenSubtitlesCredentials): List<OpenSubtitlesLanguage>
    fun search(credentials: OpenSubtitlesCredentials, query: OpenSubtitlesQuery, page: Int): OpenSubtitlesSearchPage
    fun download(
        credentials: OpenSubtitlesCredentials, result: OpenSubtitlesResult, cacheDir: File,
    ): OpenSubtitlesDownload
    fun cancel()
}

private class OpenSubtitlesClientService : OpenSubtitlesSearchService {
    private val client = OpenSubtitlesClient()
    override fun languages(credentials: OpenSubtitlesCredentials) = client.languages(credentials)
    override fun search(credentials: OpenSubtitlesCredentials, query: OpenSubtitlesQuery, page: Int) =
        client.search(credentials, query, page)
    override fun download(credentials: OpenSubtitlesCredentials, result: OpenSubtitlesResult, cacheDir: File) =
        client.download(credentials, result, cacheDir)
    override fun cancel() = client.cancel()
}

@Suppress("TooManyFunctions") // One owner for the dialog's views, operations, and lifetime.
internal class OpenSubtitlesSearchDialog(
    private val activity: MPVActivity,
    initialQuery: OpenSubtitlesQuery,
    private val localFile: File?,
    private val onSubtitle: (File, OpenSubtitlesResult) -> Unit,
    private val onDismiss: () -> Unit,
    private val service: OpenSubtitlesSearchService = OpenSubtitlesClientService(),
    private val credentialsProvider: () -> OpenSubtitlesCredentials? = {
        if (OpenSubtitlesSettings.isAvailable(activity)) OpenSubtitlesSettings.credentials(activity) else null
    },
    private val saveLanguage: (String) -> Unit = { OpenSubtitlesSettings.setLanguage(activity, it) },
    private val downloadedSubtitles: () -> Map<Int, File> = { emptyMap() },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "OpenSubtitles-UI").apply { isDaemon = true }
    }
    private val gate = OpenSubtitlesRequestGate()
    private val redactions = linkedSetOf<String>()
    private var task: Future<*>? = null
    private var closed = false
    private var closing = false
    var restorePlaybackOnDismiss = true
        private set
    private var shown = false
    private var busy = false
    private var resizePending = false
    private val resizeRunnable = Runnable {
        resizePending = false
        performResizeContent()
    }
    private var lastQuery: OpenSubtitlesQuery? = null
    private var page = 0
    private var totalPages = 0
    private var languagesLoaded = false
    private var panelMaxHeight = 0
    private var languageDialog: OpenSubtitlesLanguageDialog? = null
    private var validatedDownloads: Map<Int, File> = emptyMap()
    private var hash = initialQuery.movieHash
    private var episodeMode = initialQuery.season != null || initialQuery.episode != null
    private val root = LayoutInflater.from(activity).inflate(R.layout.dialog_opensubtitles_search, null)
    private val title = root.findViewById<EditText>(R.id.osTitle)
    private val season = root.findViewById<EditText>(R.id.osSeason)
    private val episode = root.findViewById<EditText>(R.id.osEpisode)
    private val movieModeButton = root.findViewById<Button>(R.id.osMovie)
    private val episodeModeButton = root.findViewById<Button>(R.id.osEpisodeMode)
    private val language = root.findViewById<Button>(R.id.osLanguage)
    private val search = root.findViewById<Button>(R.id.osSearch)
    private val more = root.findViewById<Button>(R.id.osMore)
    private val status = root.findViewById<TextView>(R.id.osStatus)
    private val progress = root.findViewById<ProgressBar>(R.id.osProgress)
    private val list = root.findViewById<ListView>(R.id.osResults)
    private val resultsViewport = root.findViewById<View>(R.id.osResultsViewport)
    private val formViewport = root.findViewById<MaxHeightScrollView>(R.id.osFormViewport)
    private val resultsScroll = OpenSubtitlesResultsScroll(list, root.findViewById(R.id.osResultsScrollbarThumb))
    private val adapter = OpenSubtitlesResultsAdapter(::safeText)
    private var languageCode = OpenSubtitlesSettings.language(activity).ifBlank { "en" }
    private var languages = listOf(OpenSubtitlesLanguage(
        languageCode, Locale.forLanguageTag(languageCode).displayLanguage,
    ))
    private val dialog = AlertDialog.Builder(activity).setView(root)
        .setOnDismissListener { finish() }.create()

    init {
        title.setText(initialQuery.title)
        season.setText(initialQuery.season?.let { String.format(Locale.US, "%d", it) }.orEmpty())
        episode.setText(initialQuery.episode?.let { String.format(Locale.US, "%d", it) }.orEmpty())
        sizeEpisodeFields()
        movieModeButton.setOnClickListener { setEpisodeMode(false) }
        episodeModeButton.setOnClickListener { setEpisodeMode(true) }
        renderEpisodeMode()
        installLanguages(languages)
        language.setOnClickListener { showLanguages() }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (!busy) download(adapter.getItem(position))
        }
        search.setOnClickListener { searchForm() }
        more.setOnClickListener { lastQuery?.let { loadPage(it, page + 1) } }
        root.findViewById<Button>(R.id.osClose).setOnClickListener { close() }
        title.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { searchForm(); true } else false
        }
        renderBusy(false)
    }

    fun show() {
        if (closed || shown) return
        if (activity.isFinishing || activity.isDestroyed) return
        shown = true
        activity.showWidePlayerDialog(dialog, PlayerDialogLayout(
            widthFraction = 0.72f, maxWidthDp = 720f,
            heightFraction = 0.98f, maxHeightDp = 560f,
        ))
        panelMaxHeight = dialog.window?.attributes?.height ?: 0
        resizeContent()
        search.requestFocus()
        if (credentials() == null) showStatus(R.string.os_search_unavailable) else loadLanguages()
    }

    fun close(restorePlayback: Boolean = true) {
        if (closed) return
        restorePlaybackOnDismiss = restorePlayback
        closing = true
        languageDialog?.close()
        dialog.dismiss()
        finish()
    }

    private fun finish() {
        if (closed) return
        closed = true
        closing = true
        resizePending = false
        handler.removeCallbacks(resizeRunnable)
        gate.close()
        service.cancel()
        task?.cancel(true)
        worker.shutdownNow()
        resultsScroll.close()
        val child = languageDialog
        languageDialog = null
        child?.close()
        language.isEnabled = false
        // Keep queued completions: rejected downloads still own a cache file to clean up.
        onDismiss()
    }

    private fun credentials(): OpenSubtitlesCredentials? = credentialsProvider()?.also { auth ->
        if (auth.apiKey.isNotEmpty()) redactions += auth.apiKey
        if (auth.token.isNotEmpty()) redactions += auth.token
    }

    private fun searchForm() {
        if (closed) return
        val parsed = OpenSubtitlesSearchForm.parse(title.text.toString(), languageCode,
            if (episodeMode) season.text.toString() else "",
            if (episodeMode) episode.text.toString() else "")
        if (parsed.error != null) {
            val message = when (parsed.error) {
                OpenSubtitlesSearchForm.Error.TITLE -> R.string.os_search_title_required
                OpenSubtitlesSearchForm.Error.EPISODE_PAIR -> R.string.os_search_episode_pair
                OpenSubtitlesSearchForm.Error.EPISODE_INVALID -> R.string.os_search_episode_invalid
            }
            showStatus(message)
            (if (parsed.error == OpenSubtitlesSearchForm.Error.TITLE) title else season).requestFocus()
            return
        }
        submitSearch(checkNotNull(parsed.query))
    }

    private fun setEpisodeMode(value: Boolean) {
        if (closed || episodeMode == value) return
        episodeMode = value
        gate.next()
        service.cancel()
        task?.cancel(true)
        lastQuery = null
        page = 0
        totalPages = 0
        adapter.replace(emptyList())
        status.text = ""
        renderEpisodeMode()
        renderBusy(false)
        if (!languagesLoaded) loadLanguages()
    }

    private fun renderEpisodeMode() {
        season.visibility = if (episodeMode) View.VISIBLE else View.GONE
        episode.visibility = season.visibility
        movieModeButton.isActivated = !episodeMode
        episodeModeButton.isActivated = episodeMode
        movieModeButton.contentDescription = activity.getString(R.string.os_search_movie)
        episodeModeButton.contentDescription = activity.getString(R.string.os_search_episode)
        title.nextFocusDownId = if (episodeMode) season.id else language.id
        title.nextFocusForwardId = title.nextFocusDownId
        language.nextFocusLeftId = if (episodeMode) episode.id else View.NO_ID
    }

    private fun sizeEpisodeFields() {
        for (field in listOf(season, episode)) {
            val hintWidth = kotlin.math.ceil(field.paint.measureText(field.hint.toString())).toInt()
            val width = maxOf(Utils.convertDp(activity, EPISODE_FIELD_WIDTH_DP),
                hintWidth + field.compoundPaddingLeft + field.compoundPaddingRight)
            field.layoutParams = field.layoutParams.apply { this.width = width }
        }
    }

    private fun submitSearch(query: OpenSubtitlesQuery) {
        if (credentials() == null) { showStatus(R.string.os_search_unavailable); return }
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(title.windowToken, 0)
        lastQuery = query
        page = 0
        totalPages = 0
        adapter.replace(emptyList())
        resultsScroll.resetSelection()
        loadPage(query, 1)
    }

    private fun loadPage(query: OpenSubtitlesQuery, requestedPage: Int) {
        if (closed || requestedPage !in 1..MAX_PAGES) return
        val auth = credentials() ?: run { showStatus(R.string.os_search_unavailable); return }
        val ticket = begin(R.string.os_search_loading)
        val downloaded = downloadedSubtitles().toMap()
        task = worker.submit {
            val outcome = runCatching {
                val effective = queryWithHash(query)
                val response = service.search(auth, effective, requestedPage)
                PageSelection(effective, response, downloaded.filterValues(::validFile))
            }
            post(ticket, auth) {
                renderBusy(false)
                outcome.fold(onSuccess = { selection ->
                    validatedDownloads = selection.downloaded
                    publishPage(selection.query, selection.page, requestedPage, validatedDownloads.keys)
                }, onFailure = { showFailure(it, false); updateMore() })
            }
        }
    }

    private fun queryWithHash(query: OpenSubtitlesQuery): OpenSubtitlesQuery {
        if (hash == null && localFile != null) {
            hash = try { openSubtitlesMovieHash(localFile) } catch (error: java.io.IOException) {
                if (Thread.currentThread().isInterrupted) throw error
                null
            }
        }
        return query.copy(movieHash = hash)
    }

    private fun publishPage(
        query: OpenSubtitlesQuery,
        response: OpenSubtitlesSearchPage,
        requestedPage: Int,
        downloadedIds: Set<Int>? = null,
    ) {
        lastQuery = query
        page = requestedPage
        totalPages = response.totalPages.coerceAtLeast(requestedPage)
        val incoming = response.results.asSequence()
        val combined = if (requestedPage == 1) incoming else adapter.snapshot().asSequence() + incoming
        adapter.replace(combined.distinctBy { it.fileId }.take(MAX_RESULTS).toList(), downloadedIds)
        if (adapter.count == 0) showStatus(R.string.os_search_empty)
        else if (atLimit()) showStatus(R.string.os_search_limit)
        else status.text = activity.getString(R.string.os_search_result_count, adapter.count)
        updateMore()
        if (requestedPage == 1 && adapter.count > 0) {
            resultsScroll.resetSelection()
            list.requestFocus()
            list.setSelectionFromTop(0, 0)
        }
        if (!languagesLoaded) loadLanguages()
    }

    private fun download(result: OpenSubtitlesResult) {
        if (closed || busy) return
        val cached = downloadedSubtitles()[result.fileId]
        if (cached != null && validatedDownloads[result.fileId] == cached) {
            // This file is player-owned: selection must not release, delete, or download it again.
            try { onSubtitle(cached, result) } finally { close() }
        } else {
            val auth = credentials()
            if (cached == null && auth == null) showStatus(R.string.os_search_unavailable)
            else startDownload(result, cached, auth)
        }
    }

    private fun startDownload(result: OpenSubtitlesResult, cached: File?, auth: OpenSubtitlesCredentials?) {
        val ticket = begin(R.string.os_search_downloading)
        task = worker.submit {
            val outcome = runCatching { selectDownload(cached, auth, result) }
            val completionAuth = if (outcome.getOrNull()?.newlyOwned == false) null else auth
            post(ticket, completionAuth, onRejected = {
                outcome.getOrNull()?.takeIf { it.newlyOwned }?.let {
                    OpenSubtitlesDownloadUi.discard(it.download.file, activity.cacheDir)
                }
            }) {
                renderBusy(false)
                outcome.fold(onSuccess = { selection ->
                    deliverDownload(selection, result)
                }, onFailure = { showFailure(it, true) })
            }
        }
    }

    private fun deliverDownload(selection: DownloadSelection, result: OpenSubtitlesResult) {
        if (!selection.newlyOwned && downloadedSubtitles()[result.fileId] != selection.download.file) {
            validatedDownloads = validatedDownloads - result.fileId
            adapter.setDownloadedIds(validatedDownloads.keys)
            showStatus(R.string.os_search_download_error)
        } else {
            try {
                onSubtitle(selection.download.file, result)
                if (selection.newlyOwned) showDownloadQuota(selection.download)
            } finally { close() }
        }
    }

    private fun selectDownload(
        cached: File?, auth: OpenSubtitlesCredentials?, result: OpenSubtitlesResult,
    ): DownloadSelection {
        if (cached != null && validFile(cached)) {
            return DownloadSelection(OpenSubtitlesDownload(cached, null, null), newlyOwned = false)
        }
        val downloaded = service.download(checkNotNull(auth), result, activity.cacheDir)
        if (!validFile(downloaded.file)) {
            OpenSubtitlesDownloadUi.discard(downloaded.file, activity.cacheDir)
            error("Invalid subtitle download")
        }
        return DownloadSelection(downloaded, newlyOwned = true)
    }

    private fun validFile(file: File): Boolean = try {
        file.isFile && file.length() > 0L
    } catch (_: SecurityException) {
        false
    }

    private fun showDownloadQuota(downloaded: OpenSubtitlesDownload) {
        val remaining = downloaded.remaining?.takeIf { it >= 0 } ?: return
        val reset = OpenSubtitlesDownloadUi.resetDate(downloaded.resetTime)
        val message = if (reset == null) activity.getString(R.string.os_search_download_remaining, remaining)
        else activity.getString(R.string.os_search_download_remaining_reset, remaining,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(reset))
        activity.showToast(message)
    }

    private fun loadLanguages() {
        if (closed) return
        val auth = credentials() ?: return
        val ticket = gate.next()
        task = worker.submit {
            val outcome = runCatching { service.languages(auth) }
            post(ticket, auth) {
                outcome.fold(onSuccess = { options ->
                    val valid = options.filter { OPEN_SUBTITLES_LANGUAGE_CODE.matches(it.code) }
                        .distinctBy { it.code }.sortedBy { it.name }.take(MAX_LANGUAGES)
                    if (valid.isNotEmpty()) {
                        languagesLoaded = true
                        installLanguages(if (valid.any { it.code == languageCode }) valid else languages + valid)
                    }
                }, onFailure = { showFailure(it, false, R.string.os_search_language_error) })
            }
        }
    }

    private fun installLanguages(options: List<OpenSubtitlesLanguage>) {
        languages = options
        val selected = options.firstOrNull { it.code == languageCode }
        language.text = selected?.let { safeText(it.name).ifBlank { it.code } + " (" + it.code + ")" }
            ?: languageCode
    }

    private fun showLanguages() {
        if (closed || languageDialog != null) return
        languageDialog = OpenSubtitlesLanguageDialog(activity,
            languages.map { it.copy(name = safeText(it.name).ifBlank { it.code }) }, languageCode,
            onSelected = { selected ->
                if (!closed && selected != languageCode) {
                    languageCode = selected
                    saveLanguage(selected)
                    installLanguages(languages)
                }
            },
            onDismiss = {
                languageDialog = null
                if (!closing && !closed && dialog.isShowing) language.requestFocus()
            },
        ).also { it.show() }
    }

    private fun begin(message: Int): Int {
        val ticket = gate.next()
        service.cancel()
        task?.cancel(true)
        renderBusy(true)
        showStatus(message)
        return ticket
    }

    private fun post(
        ticket: Int, auth: OpenSubtitlesCredentials?, onRejected: () -> Unit = {}, action: () -> Unit,
    ) {
        if (!gate.accepts(ticket)) { onRejected(); return }
        val queued = handler.post {
            if (!gate.accepts(ticket) || activity.isFinishing || activity.isDestroyed) {
                onRejected()
                return@post
            }
            if (auth != null && credentials() != auth) {
                renderBusy(false)
                showStatus(R.string.os_search_unavailable)
                onRejected()
                return@post
            }
            action()
        }
        if (!queued) onRejected()
    }

    private fun renderBusy(value: Boolean) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.INVISIBLE
        list.isEnabled = !value
        list.alpha = if (value) DISABLED_ALPHA else 1f
        // Keep Search and the form usable so a new query can supersede an in-flight request.
        updateMore()
    }

    private fun atLimit(): Boolean = page >= MAX_PAGES || adapter.count >= MAX_RESULTS
    private fun updateMore() {
        more.visibility = if (page > 0 && page < totalPages && !atLimit()) View.VISIBLE else View.GONE
        more.isEnabled = !busy
        val footer = if (more.visibility == View.VISIBLE) R.id.osMore else R.id.osClose
        list.nextFocusDownId = footer
        list.nextFocusForwardId = footer
        val resultsTarget = if (adapter.count > 0 && !busy) R.id.osResults else footer
        for (control in listOf(season, episode, language, search)) control.nextFocusDownId = resultsTarget
        resizeContent()
    }

    private fun resizeContent() {
        if (!shown || closed || panelMaxHeight <= 0 || resizePending) return
        resizePending = true
        handler.post(resizeRunnable)
    }

    private fun performResizeContent() {
        if (!shown || closed || panelMaxHeight <= 0) return
        val rowHeight = if (adapter.count > 0) adapter.measureRowHeight(list) else 0
        val extent = rowHeight + list.dividerHeight
        val visibleRows = adapter.count.coerceAtMost(VISIBLE_RESULTS)
        val desiredHeight = if (visibleRows > 0) visibleRows * extent - list.dividerHeight else 0
        fitOpenSubtitlesPanel(dialog, root, resultsViewport, panelMaxHeight, desiredHeight,
            rowExtent = extent, rowDivider = list.dividerHeight, flexibleForm = formViewport)
        resultsScroll.rowHeight = rowHeight
    }

    private fun showStatus(message: Int) { status.setText(message); resizeContent() }
    private fun showFailure(error: Throwable, downloading: Boolean, fallback: Int? = null) {
        val failure = error as? OpenSubtitlesException
        android.util.Log.w("mpv", "OpenSubtitles ${if (downloading) "download" else "search"} failed: " +
            "reason=${failure?.reason?.name ?: "UNKNOWN"}, status=${failure?.status ?: "none"}, " +
            "stage=${failure?.stage?.name ?: "none"}")
        showStatus(openSubtitlesSearchFailureMessage(
            failure, !credentialsProvider()?.token.isNullOrBlank(),
            fallback ?: if (downloading) R.string.os_search_download_error else R.string.os_search_error,
        ))
        if (failure?.status != null) {
            status.text = activity.getString(R.string.os_error_http_status, status.text, failure.status)
            resizeContent()
        }
    }

    private fun safeText(value: String): String {
        var cleaned = value
        // Retain redactions across account changes without decrypting settings for each row.
        for (secret in redactions) cleaned = cleaned.replace(secret, "[redacted]")
        return cleaned.filter { !it.isISOControl() }.take(MAX_DISPLAY_LENGTH)
    }

    private companion object {
        const val VISIBLE_RESULTS = 3
        const val EPISODE_FIELD_WIDTH_DP = 112f
        const val MAX_PAGES = 10
        const val MAX_RESULTS = 500
        const val MAX_LANGUAGES = 300
        const val MAX_DISPLAY_LENGTH = 500
        const val DISABLED_ALPHA = 0.6f
    }

    private data class PageSelection(
        val query: OpenSubtitlesQuery, val page: OpenSubtitlesSearchPage, val downloaded: Map<Int, File>,
    )
    private data class DownloadSelection(val download: OpenSubtitlesDownload, val newlyOwned: Boolean)
}
