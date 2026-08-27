package app.mpvnova.player.preferences

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

internal fun showSupportBundleExportFlow(activity: Activity, bundle: File) {
    ArchiveExportFlow(activity, bundle, SUPPORT_EXPORT_LABELS).show()
}

internal fun showFullBackupExportFlow(activity: Activity, backup: File) {
    ArchiveExportFlow(activity, backup, FULL_BACKUP_EXPORT_LABELS).show()
}

private class ArchiveExportFlow(
    private val activity: Activity,
    private val archive: File,
    private val labels: ArchiveExportLabels,
) {
    fun show() {
        val options = mutableListOf(
            ArchiveExportOption(activity.getString(R.string.support_export_save_downloads)) {
                saveToDownloads()
            }
        )
        queryShareTargets()
            .firstOrNull { it.packageName == LOCALSEND_PACKAGE }
            ?.let { target ->
                options += ArchiveExportOption(
                    activity.getString(R.string.support_export_share_localsend)
                ) { launchShareTarget(target) }
            }
        options += ArchiveExportOption(activity.getString(R.string.support_export_share_other)) {
            showShareTargetDialog()
        }
        activity.showSettingsChoiceDialog(
            activity.getString(labels.chooser),
            options.map { SettingsChoiceItem(title = it.label, onClick = it.action) },
        )
    }

    private fun saveToDownloads() {
        if (needsLegacyDownloadsPermission()) {
            pendingLegacyDownloadsFlow = this
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_DOWNLOADS,
            )
        } else {
            saveToDownloadsAfterPermission()
        }
    }

    fun saveToDownloadsAfterPermission() {
        val progress = activity.showSettingsProgressDialog(activity.getString(labels.saving))
        ARCHIVE_EXPORT_IO_EXECUTOR.execute {
            val result = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToDownloadsMediaStore()
                } else {
                    saveToLegacyDownloads()
                }
            }
            activity.runOnUiThread {
                progress.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { savedName ->
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.support_export_saved, savedName),
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure {
                    showSaveFailure()
                }
            }
        }
    }

    fun showSaveFailure() {
        Toast.makeText(activity, labels.saveFailed, Toast.LENGTH_LONG).show()
    }

    private fun needsLegacyDownloadsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloadsMediaStore(): String {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, archive.name)
            put(MediaStore.Downloads.MIME_TYPE, ARCHIVE_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Could not create Downloads entry" }
        runCatching {
            checkNotNull(resolver.openOutputStream(uri)) {
                "Could not open Downloads entry"
            }.use { output -> archive.inputStream().use { input -> input.copyTo(output) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrThrow()
        return archive.name
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDownloads(): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw IOException("Could not create Downloads directory")
        }
        val target = uniqueDownloadFile(downloads, archive.name)
        archive.copyTo(target, overwrite = false)
        return target.name
    }

    private fun showShareTargetDialog() {
        val targets = queryShareTargets().filter { it.packageName != LOCALSEND_PACKAGE }
        if (targets.isEmpty()) {
            Toast.makeText(activity, labels.noTarget, Toast.LENGTH_SHORT).show()
            return
        }
        activity.showSettingsChoiceDialog(
            activity.getString(labels.shareTargetTitle),
            targets.map { target ->
                SettingsChoiceItem(title = target.label) { launchShareTarget(target) }
            },
        )
    }

    private fun queryShareTargets(): List<ArchiveShareTarget> {
        val shareIntent = buildShareIntent().first
        val packageManager = activity.packageManager
        val targets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return targets
            .mapNotNull { it.toArchiveShareTarget(activity) }
            .distinctBy { "${it.packageName}/${it.className}" }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun launchShareTarget(target: ArchiveShareTarget) {
        val (shareIntent, uri) = buildShareIntent()
        shareIntent.component = ComponentName(target.packageName, target.className)
        try {
            activity.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(shareIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, labels.shareFailed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, labels.shareFailed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildShareIntent(): Pair<Intent, Uri> {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            archive,
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType(ARCHIVE_MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, archive.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData = ClipData.newUri(activity.contentResolver, archive.name, uri)
        return shareIntent to uri
    }
}

private fun uniqueDownloadFile(directory: File, filename: String): File {
    var target = File(directory, filename)
    if (!target.exists()) return target
    val base = target.nameWithoutExtension
    val extension = target.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
    var index = 2
    do {
        target = File(directory, "$base-$index$extension")
        index++
    } while (target.exists())
    return target
}

private fun ResolveInfo.toArchiveShareTarget(context: Context): ArchiveShareTarget? {
    val info = activityInfo ?: return null
    val label = loadLabel(context.packageManager).toString().takeIf(String::isNotBlank)
        ?: info.packageName
    return ArchiveShareTarget(info.packageName, info.name, label)
}

fun handleSupportExportPermissionResult(
    requestCode: Int,
    grantResults: IntArray,
) {
    if (requestCode != REQUEST_WRITE_DOWNLOADS) return
    val pendingFlow = pendingLegacyDownloadsFlow
    pendingLegacyDownloadsFlow = null
    if (pendingFlow != null) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingFlow.saveToDownloadsAfterPermission()
        } else {
            pendingFlow.showSaveFailure()
        }
    }
}

fun clearPendingSupportExportFlow() {
    pendingLegacyDownloadsFlow = null
}

private data class ArchiveExportOption(val label: String, val action: () -> Unit)

private data class ArchiveExportLabels(
    val chooser: Int,
    val saving: Int,
    val saveFailed: Int,
    val noTarget: Int,
    val shareTargetTitle: Int,
    val shareFailed: Int,
)

private data class ArchiveShareTarget(
    val packageName: String,
    val className: String,
    val label: String,
)

private const val LOCALSEND_PACKAGE = "org.localsend.localsend_app"
private const val ARCHIVE_MIME_TYPE = "application/zip"
private const val REQUEST_WRITE_DOWNLOADS = 24061
private val ARCHIVE_EXPORT_IO_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mpvNova-archive-export")
}

private val SUPPORT_EXPORT_LABELS = ArchiveExportLabels(
    chooser = R.string.support_export_chooser,
    saving = R.string.support_export_saving,
    saveFailed = R.string.support_export_save_failed,
    noTarget = R.string.support_export_no_target,
    shareTargetTitle = R.string.support_export_share_target_title,
    shareFailed = R.string.support_export_share_failed,
)

private val FULL_BACKUP_EXPORT_LABELS = ArchiveExportLabels(
    chooser = R.string.full_backup_export_destination,
    saving = R.string.full_backup_export_saving,
    saveFailed = R.string.full_backup_export_failed,
    noTarget = R.string.full_backup_export_no_target,
    shareTargetTitle = R.string.full_backup_export_share_target_title,
    shareFailed = R.string.full_backup_export_share_failed,
)

@SuppressLint("StaticFieldLeak")
private var pendingLegacyDownloadsFlow: ArchiveExportFlow? = null
