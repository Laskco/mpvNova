package app.mpvnova.player.preferences

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.media.MediaCodecList
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.MPVView
import app.mpvnova.player.MpvLogRingBuffer
import app.mpvnova.player.NativeLibraryVersion
import app.mpvnova.player.PREF_SHIELD_MPEG2_SOFTWARE_FALLBACK
import app.mpvnova.player.R
import app.mpvnova.player.Utils
import app.mpvnova.player.toShieldDecoderFallback
import app.mpvnova.player.sanitizeMpvLogText
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SupportActions {
    private val PLAYER_UI_KEYS = arrayOf(
        "display_media_title",
        "display_clock_overlay",
        "display_clock_date",
        "display_clock_on_pause",
        "display_clock_24_hour",
        "bottom_controls",
        "player_controls_timeout",
        "keep_controls_visible_paused",
        "autopause_controls_overlay",
        "autopause_shield_hi10p",
        "remote_next_chapter_button",
        "remember_player_screen_brightness",
        "player_screen_brightness_percent",
        "player_screen_brightness_initialized",
        "remember_video_contrast",
        "video_contrast",
        "remember_video_gamma",
        "video_gamma",
        "remember_video_saturation",
        "video_saturation",
        "no_ui_pause",
        "playlist_exit_warning",
        "use_time_remaining",
        "hide_controls_while_seeking",
        "minimal_seekbar_while_seeking",
    )

    fun copyDebugInfo(activity: Activity) {
        val text = buildDebugInfo(activity)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.support_debug_info_title), text))
        Toast.makeText(activity, R.string.support_debug_info_copied, Toast.LENGTH_SHORT).show()
    }

    fun exportConfigBundle(activity: Activity) {
        val progress = activity.showSettingsProgressDialog(
            activity.getString(R.string.support_export_preparing)
        )
        val activityReference = WeakReference(activity)
        val progressReference = WeakReference(progress)
        val applicationContext = activity.applicationContext
        SUPPORT_IO_EXECUTOR.execute {
            val result = runCatching { createSupportBundle(applicationContext) }
            val currentActivity = activityReference.get() ?: return@execute
            currentActivity.runOnUiThread {
                progressReference.get()?.dismiss()
                if (currentActivity.isFinishing || currentActivity.isDestroyed)
                    return@runOnUiThread
                result.onSuccess { bundle ->
                    SupportBundleExportFlow(currentActivity, bundle).show()
                }.onFailure {
                    Toast.makeText(
                        currentActivity,
                        R.string.support_export_save_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun createSupportBundle(context: Context): File {
        val supportDir = File(context.cacheDir, "support")
        if (!supportDir.exists())
            supportDir.mkdirs()
        supportDir.listFiles()?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val bundle = File(supportDir, "mpvNova-support-$stamp.zip")
        val logcat = captureCurrentProcessLogcat()
        val exitHistory = captureExitHistory(context)
        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.textEntry(
                "bundle-contents.txt",
                buildBundleManifest(logcat.available, exitHistory.available),
            )
            zip.textEntry("debug-info.txt", buildDebugInfo(context))
            zip.textEntry("settings-summary.txt", buildSettingsSummary(context))
            zip.textEntry("storage-report.txt", buildStorageReport(context))
            zip.textEntry("memory-report.txt", buildMemoryReport(context))
            zip.textEntry("media-codecs.txt", buildCodecReport())
            zip.configEntry(context, "mpv.conf")
            zip.configEntry(context, "input.conf")
            zip.textEntry("mpv-log.txt", buildMpvLogDump())
            zip.textEntry("android-logcat.txt", logcat.text)
            zip.textEntry("exit-history.txt", exitHistory.summary)
            exitHistory.traces.forEach { (name, content) -> zip.textEntry(name, content) }
            zip.crashEntries(context)
        }
        return bundle
    }

    fun resetPlayerUiSettings(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().apply {
            PLAYER_UI_KEYS.forEach(::remove)
        }.apply()
        Toast.makeText(activity, R.string.support_reset_player_ui_done, Toast.LENGTH_SHORT).show()
    }

    private fun buildDebugInfo(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val packageManager = context.packageManager
        val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val isFireTv = packageManager.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
        val isTvMode = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val hasFakeTouch = packageManager.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH)
        val autoDecoder = prefs.getBoolean("decoder_auto_fallback", true)
        val shieldDecoder = prefs.getBoolean("shield_decoder_mode", true)
        val shieldDecoderFallback = prefs.getString(
            "shield_decoder_fallback",
            MPVView.SHIELD_DECODER_FALLBACK_DEFAULT,
        ).toShieldDecoderFallback()
        val shieldMpeg2Fallback = prefs.getBoolean(
            PREF_SHIELD_MPEG2_SOFTWARE_FALLBACK,
            true,
        )
        val preferredDecoder = prefs.getString("preferred_decoder_mode", null)
            ?.takeIf { it.isNotBlank() }
            ?: "default"
        val decoder = if (autoDecoder)
            "Automatic fallback enabled; preferred=$preferredDecoder"
        else
            preferredDecoder

        return buildString {
            appendLine("mpvNova debug info")
            appendLine(
                "App version: ${BuildConfig.VERSION_NAME} " +
                    "(${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"
            )
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Fire TV: ${if (isFireTv) "yes" else "no"}")
            appendLine("TV mode: ${if (isTvMode) "yes" else "no"}")
            appendLine(
                "Input features: touchscreen=${if (hasTouchscreen) "yes" else "no"}, " +
                    "faketouch=${if (hasFakeTouch) "yes" else "no"}"
            )
            appendLine("Decoder setting: $decoder")
            appendLine("Shield decoder mode: ${if (shieldDecoder) "enabled" else "disabled"}")
            appendLine("Shield Hi10P fallback: $shieldDecoderFallback")
            appendLine("Shield MPEG2 software fallback: ${if (shieldMpeg2Fallback) "enabled" else "disabled"}")
            appendLine("mpv: ${nativeVersion(context, "libmpv.so", "mpv v")}")
            appendLine("FFmpeg: ${nativeVersion(context, "libavcodec.so", "FFmpeg version ")}")
        }
    }

    private fun buildSettingsSummary(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return buildString {
            appendLine("Selected mpvNova settings")
            prefs.all.toSortedMap().forEach { (key, value) ->
                if (key == "release_history")
                    return@forEach
                val sanitizedValue = if (SENSITIVE_SETTING_KEY.containsMatchIn(key)) {
                    "<redacted>"
                } else {
                    sanitizeMpvLogText(value.toString())
                }
                appendLine("$key=$sanitizedValue")
            }
        }
    }

    private fun ZipOutputStream.configEntry(context: Context, filename: String) {
        val file = File(context.filesDir, filename)
        val content = if (file.isFile)
            sanitizeMpvLogText(file.readText())
        else
            "$filename is not present.\n"
        textEntry(filename, content)
    }

    private fun ZipOutputStream.textEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /**
     * Emit every persistent crash file the [CrashReporter] has written and any
     * reports left in the legacy cache location into the bundle
     * under a `crashes/` subdirectory. Silently no-op when there have been
     * no crashes — which is the common case.
     */
    private fun ZipOutputStream.crashEntries(context: Context) {
        val files = listOf(
            File(context.filesDir, "diagnostics/crashes"),
            File(context.cacheDir, "crashes"),
        ).flatMap { dir ->
            dir.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }.orEmpty()
        }.distinctBy { it.name }
        if (files.isEmpty()) return
        for (file in files.sortedBy { it.lastModified() }) {
            textEntry("crashes/${file.name}", sanitizeMpvLogText(file.readText()))
        }
    }

    private fun buildMpvLogDump(): String {
        val lines = MpvLogRingBuffer.snapshot()
        if (lines.isEmpty()) {
            return "No mpv log lines captured yet in this process.\n"
        }
        return buildString {
            appendLine("Last ${lines.size} mpv log lines captured by mpvNova in this session.")
            appendLine()
            for (line in lines) {
                appendLine(line)
            }
        }
    }

    private fun nativeVersion(context: Context, libraryName: String, marker: String): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val file = nativeDir?.let { File(it, libraryName) }
        return if (file?.isFile != true) {
            "unknown"
        } else {
            runCatching {
                NativeLibraryVersion.find(file, marker) ?: "unknown"
            }.getOrDefault("unknown")
        }
    }

    private const val AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv"
}

private data class SupportCapture(
    val text: String,
    val available: Boolean,
)

private data class ExitHistoryCapture(
    val summary: String,
    val traces: Map<String, String>,
    val available: Boolean,
)

private fun buildBundleManifest(logcatAvailable: Boolean, exitHistoryAvailable: Boolean): String =
    buildString {
        appendLine("mpvNova support bundle")
        appendLine("Generated: ${Date()}")
        appendLine()
        appendLine("Always included when available:")
        appendLine("- App, Android, device, decoder, storage, memory, and codec details")
        appendLine("- Selected settings with sensitive values redacted")
        appendLine("- mpv.conf and input.conf")
        appendLine("- Current-session mpv logs")
        appendLine("- Persistent mpvNova uncaught-crash reports")
        appendLine()
        appendLine("Android process logcat captured: ${if (logcatAvailable) "yes" else "no"}")
        appendLine("Historical process exits captured: ${if (exitHistoryAvailable) "yes" else "no"}")
        appendLine(
            "URLs, authorization headers, cookies, and matching sensitive settings " +
                "are redacted from diagnostics."
        )
        appendLine("Configuration files are included with sensitive URLs and headers redacted.")
    }

private fun captureCurrentProcessLogcat(): SupportCapture {
    return runCatching {
        val process = ProcessBuilder(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            LOGCAT_LINE_LIMIT.toString(),
            "--pid=${Process.myPid()}",
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { reader ->
            readLimitedText(reader, LOGCAT_CHARACTER_LIMIT)
        }
        val exitCode = process.waitFor()
        val sanitized = sanitizeMpvLogText(text)
        if (exitCode != 0) {
            SupportCapture(
                "Android logcat was unavailable (exit $exitCode).\n$sanitized",
                false,
            )
        } else {
            SupportCapture(
                sanitized.ifBlank { "No Android log lines were available for this process.\n" },
                true,
            )
        }
    }.getOrElse { error ->
        SupportCapture(
            "Android logcat capture failed: ${error.javaClass.name}: ${error.message}\n",
            false,
        )
    }
}

private fun captureExitHistory(context: Context): ExitHistoryCapture {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return ExitHistoryCapture(
            "Historical process exits require Android 11 or newer.\n",
            emptyMap(),
            false,
        )
    }
    return runCatching {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val entries = activityManager.getHistoricalProcessExitReasons(
            context.packageName,
            0,
            EXIT_HISTORY_LIMIT,
        )
        val traces = linkedMapOf<String, String>()
        val summary = buildString {
            appendLine("Recent mpvNova process exits")
            if (entries.isEmpty()) appendLine("No historical exits were reported by Android.")
            entries.forEachIndexed { index, exit ->
                appendLine()
                appendLine("Exit ${index + 1}")
                appendLine("Timestamp: ${Date(exit.timestamp)}")
                appendLine("Reason: ${exit.reason}")
                appendLine("Status: ${exit.status}")
                appendLine("Importance: ${exit.importance}")
                appendLine("PSS/RSS KB: ${exit.pss}/${exit.rss}")
                appendLine("Description: ${sanitizeMpvLogText(exit.description.orEmpty())}")
                val trace = runCatching {
                    exit.traceInputStream?.bufferedReader()?.use { reader ->
                        readLimitedText(reader, EXIT_TRACE_CHARACTER_LIMIT)
                    }
                }.getOrNull()
                if (!trace.isNullOrBlank()) {
                    val name = "exit-traces/exit-${index + 1}.txt"
                    traces[name] = sanitizeMpvLogText(trace)
                    appendLine("Trace: $name")
                } else {
                    appendLine("Trace: unavailable")
                }
            }
        }
        ExitHistoryCapture(summary, traces, true)
    }.getOrElse { error ->
        ExitHistoryCapture(
            "Historical exit capture failed: ${error.javaClass.name}: ${error.message}\n",
            emptyMap(),
            false,
        )
    }
}

private fun buildMemoryReport(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val system = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val process = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
    val runtime = Runtime.getRuntime()
    val runtimeHeap = listOf(
        runtime.totalMemory() - runtime.freeMemory(),
        runtime.freeMemory(),
        runtime.maxMemory(),
    ).joinToString("/")
    val nativeHeap = listOf(
        Debug.getNativeHeapAllocatedSize(),
        Debug.getNativeHeapFreeSize(),
        Debug.getNativeHeapSize(),
    ).joinToString("/")
    return buildString {
        appendLine("mpvNova memory report")
        appendLine("System available/total bytes: ${system.availMem}/${system.totalMem}")
        appendLine("System low memory: ${system.lowMemory}")
        appendLine("System low-memory threshold bytes: ${system.threshold}")
        appendLine("Runtime heap used/free/max bytes: $runtimeHeap")
        appendLine("Native heap allocated/free/size bytes: $nativeHeap")
        if (process != null) {
            val processMemory = listOf(
                process.totalPss,
                process.totalPrivateDirty,
                process.totalSharedDirty,
            ).joinToString("/")
            appendLine("Process PSS/private-dirty/shared-dirty KB: $processMemory")
        }
    }
}

private fun buildCodecReport(): String = runCatching {
    buildString {
        appendLine("Android media codecs")
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.forEach { codec ->
            val implementation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                " hardware=${codec.isHardwareAccelerated} software=${codec.isSoftwareOnly} vendor=${codec.isVendor}"
            } else {
                ""
            }
            appendLine("${codec.name} encoder=${codec.isEncoder}$implementation")
            appendLine("  ${codec.supportedTypes.joinToString()}")
        }
    }
}.getOrElse { error ->
    "Codec enumeration failed: ${error.javaClass.name}: ${error.message}\n"
}

private fun readLimitedText(reader: java.io.BufferedReader, maximumCharacters: Int): String {
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    return buildString {
        while (length < maximumCharacters) {
            val amount = reader.read(
                buffer,
                0,
                minOf(buffer.size, maximumCharacters - length),
            )
            if (amount < 0) break
            append(buffer, 0, amount)
        }
        if (length >= maximumCharacters) appendLine("\n[output truncated by mpvNova]")
    }
}

@Suppress("DEPRECATION")
private fun buildStorageReport(context: Context): String {
    return buildString {
        appendLine("mpvNova storage report")
        appendLine()
        appendLine("External storage directory")
        appendLine(Environment.getExternalStorageDirectory().describeStoragePath())
        appendLine()
        appendLine("externalMediaDirs")
        context.externalMediaDirs.forEachIndexed { index, file ->
            appendLine("$index: ${file?.describeStoragePath() ?: "null"}")
        }
        appendLine()
        appendLine("mpvNova detected storage volumes")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                Utils.getStorageVolumes(context)
            }.onSuccess { volumes ->
                if (volumes.isEmpty()) {
                    appendLine("No readable volumes detected.")
                } else {
                    volumes.forEachIndexed { index, volume ->
                        appendLine("$index: ${volume.description} -> ${volume.path.describeStoragePath()}")
                    }
                }
            }.onFailure { error ->
                appendLine("Storage volume detection failed: ${error.javaClass.name}: ${error.message}")
            }
        } else {
            appendLine("Volume detection requires Android 7+.")
        }
        appendLine()
        appendLine("/proc/mounts storage entries")
        runCatching {
            File("/proc/mounts").forEachLine { line ->
                if (line.contains("/storage") || line.contains("/mnt/media_rw")) {
                    appendLine(line)
                }
            }
        }.onFailure { error ->
            appendLine("/proc/mounts read failed: ${error.javaClass.name}: ${error.message}")
        }
    }
}

private fun File.describeStoragePath(): String {
    return "$absolutePath exists=${exists()} canRead=${canRead()} isDirectory=${isDirectory()}"
}

private class SupportBundleExportFlow(
    private val activity: Activity,
    private val bundle: File
) {
    fun show() {
        val options = mutableListOf<SupportExportOption>()
        options.add(
            SupportExportOption(
                activity.getString(R.string.support_export_save_downloads)
            ) {
                saveBundleToDownloads()
            }
        )

        querySupportBundleTargets()
            .firstOrNull { it.packageName == LOCALSEND_PACKAGE }
            ?.let { target ->
                options.add(
                    SupportExportOption(
                        activity.getString(R.string.support_export_share_localsend)
                    ) {
                        launchShareTarget(target)
                    }
                )
            }

        options.add(
            SupportExportOption(
                activity.getString(R.string.support_export_share_other)
            ) {
                showShareTargetDialog()
            }
        )

        activity.showSettingsChoiceDialog(
            activity.getString(R.string.support_export_chooser),
            options.map { option ->
                SettingsChoiceItem(title = option.label, onClick = option.action)
            },
        )
    }

    private fun saveBundleToDownloads() {
        if (needsLegacyDownloadsPermission()) {
            pendingLegacyDownloadsFlow = this
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_DOWNLOADS
            )
            return
        }
        saveBundleToDownloadsAfterPermission()
    }

    fun saveBundleToDownloadsAfterPermission() {
        val progress = activity.showSettingsProgressDialog(
            activity.getString(R.string.support_export_saving)
        )
        SUPPORT_IO_EXECUTOR.execute {
            val result = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveBundleToDownloadsMediaStore()
                } else {
                    saveBundleToLegacyDownloads()
                }
            }
            activity.runOnUiThread {
                progress.dismiss()
                if (activity.isFinishing || activity.isDestroyed)
                    return@runOnUiThread
                result.onSuccess { savedName ->
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.support_export_saved, savedName),
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure {
                    Toast.makeText(activity, R.string.support_export_save_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun needsLegacyDownloadsPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBundleToDownloadsMediaStore(): String {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, bundle.name)
            put(MediaStore.Downloads.MIME_TYPE, SUPPORT_BUNDLE_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "Could not create Downloads entry"
        }
        runCatching {
            checkNotNull(resolver.openOutputStream(uri)) {
                "Could not open Downloads entry"
            }.use { output ->
                bundle.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrThrow()
        return bundle.name
    }

    @Suppress("DEPRECATION")
    private fun saveBundleToLegacyDownloads(): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs())
            throw IOException("Could not create Downloads directory")
        val target = uniqueDownloadFile(downloads, bundle.name)
        bundle.copyTo(target, overwrite = false)
        return target.name
    }

    private fun uniqueDownloadFile(directory: File, filename: String): File {
        var target = File(directory, filename)
        if (!target.exists())
            return target

        val base = target.nameWithoutExtension
        val extension = target.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 2
        do {
            target = File(directory, "$base-$index$extension")
            index++
        } while (target.exists())
        return target
    }

    private fun showShareTargetDialog() {
        val targets = querySupportBundleTargets()
            .filter { it.packageName != LOCALSEND_PACKAGE }
        if (targets.isEmpty()) {
            Toast.makeText(activity, R.string.support_export_no_target, Toast.LENGTH_SHORT).show()
            return
        }

        activity.showSettingsChoiceDialog(
            activity.getString(R.string.support_export_share_target_title),
            targets.map { target ->
                SettingsChoiceItem(title = target.label) { launchShareTarget(target) }
            },
        )
    }

    private fun querySupportBundleTargets(): List<SupportShareTarget> {
        val shareIntent = buildShareIntent().first
        val packageManager = activity.packageManager
        val targets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return targets
            .mapNotNull { it.toSupportShareTarget(activity) }
            .distinctBy { "${it.packageName}/${it.className}" }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun launchShareTarget(target: SupportShareTarget) {
        val (shareIntent, uri) = buildShareIntent()
        shareIntent.component = ComponentName(target.packageName, target.className)
        try {
            activity.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(shareIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.support_export_share_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.support_export_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildShareIntent(): Pair<Intent, Uri> {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            bundle
        )
        val streamClip = ClipData.newUri(activity.contentResolver, bundle.name, uri)
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType(SUPPORT_BUNDLE_MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, bundle.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData = streamClip
        return shareIntent to uri
    }
}

private data class SupportExportOption(
    val label: String,
    val action: () -> Unit
)

private data class SupportShareTarget(
    val packageName: String,
    val className: String,
    val label: String
)

private fun ResolveInfo.toSupportShareTarget(context: Context): SupportShareTarget? {
    val info = activityInfo ?: return null
    val label = loadLabel(context.packageManager)
        .toString()
        .takeIf { it.isNotBlank() }
        ?: info.packageName
    return SupportShareTarget(
        packageName = info.packageName,
        className = info.name,
        label = label
    )
}

fun handleSupportExportPermissionResult(
    activity: Activity,
    requestCode: Int,
    grantResults: IntArray
) {
    if (requestCode == REQUEST_WRITE_DOWNLOADS) {
        val pendingFlow = pendingLegacyDownloadsFlow
        pendingLegacyDownloadsFlow = null
        if (pendingFlow != null) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                pendingFlow.saveBundleToDownloadsAfterPermission()
            else
                Toast.makeText(activity, R.string.support_export_save_failed, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Drop a pending export flow when its host activity is destroyed; the flow holds
 * that activity, so leaving it parked here across a recreate leaks the instance.
 */
fun clearPendingSupportExportFlow() {
    pendingLegacyDownloadsFlow = null
}

private const val LOCALSEND_PACKAGE = "org.localsend.localsend_app"
private const val SUPPORT_BUNDLE_MIME_TYPE = "application/zip"
private const val REQUEST_WRITE_DOWNLOADS = 24061
private const val LOGCAT_LINE_LIMIT = 3000
private const val LOGCAT_CHARACTER_LIMIT = 2 * 1024 * 1024
private const val EXIT_HISTORY_LIMIT = 10
private const val EXIT_TRACE_CHARACTER_LIMIT = 512 * 1024
private val SUPPORT_IO_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mpvNova-support-io")
}
private val SENSITIVE_SETTING_KEY = Regex(
    "(?i)(authorization|cookie|credential|password|secret|token|uri|path)",
)

// Bridges the permission-result round-trip, which has no instance to hang state
// off. Cleared by the result handler and by PreferenceActivity.onDestroy, so the
// activity inside the flow can't outlive its host.
@SuppressLint("StaticFieldLeak")
private var pendingLegacyDownloadsFlow: SupportBundleExportFlow? = null
