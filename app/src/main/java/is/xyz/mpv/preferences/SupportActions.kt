package app.mpvnova.player.preferences

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SupportActions {
    private val PLAYER_UI_KEYS = arrayOf(
        "auto_rotation",
        "display_media_title",
        "bottom_controls",
        "player_controls_timeout",
        "keep_controls_visible_paused",
        "no_ui_pause",
        "playlist_exit_warning",
        "use_time_remaining",
    )

    fun copyDebugInfo(activity: Activity) {
        val text = buildDebugInfo(activity)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.support_debug_info_title), text))
        Toast.makeText(activity, R.string.support_debug_info_copied, Toast.LENGTH_SHORT).show()
    }

    fun exportConfigBundle(activity: Activity) {
        val supportDir = File(activity.cacheDir, "support")
        if (!supportDir.exists())
            supportDir.mkdirs()
        supportDir.listFiles()?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val bundle = File(supportDir, "mpvNova-support-$stamp.zip")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.textEntry("debug-info.txt", buildDebugInfo(activity))
            zip.textEntry("settings-summary.txt", buildSettingsSummary(activity))
            zip.configEntry(activity, "mpv.conf")
            zip.configEntry(activity, "input.conf")
            zip.textEntry(
                "logs.txt",
                "mpvNova does not keep a persistent log file yet.\n" +
                    "For runtime playback logs, capture logcat while reproducing the issue.\n"
            )
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            bundle
        )
        val streamClip = ClipData.newUri(activity.contentResolver, bundle.name, uri)
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.clipData = streamClip
        val chooser = Intent.createChooser(shareIntent, activity.getString(R.string.support_export_chooser))
        chooser.clipData = streamClip
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.support_export_no_target, Toast.LENGTH_SHORT).show()
        }
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
        val autoDecoder = prefs.getBoolean("decoder_auto_fallback", true)
        val preferredDecoder = prefs.getString("preferred_decoder_mode", null)
            ?.takeIf { it.isNotBlank() }
            ?: "default"
        val decoder = if (autoDecoder)
            "Automatic fallback enabled; preferred=$preferredDecoder"
        else
            preferredDecoder

        return buildString {
            appendLine("mpvNova debug info")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Decoder setting: $decoder")
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
                appendLine("$key=$value")
            }
        }
    }

    private fun ZipOutputStream.configEntry(context: Context, filename: String) {
        val file = File(context.filesDir, filename)
        val content = if (file.isFile)
            file.readText()
        else
            "$filename is not present.\n"
        textEntry(filename, content)
    }

    private fun ZipOutputStream.textEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun nativeVersion(context: Context, libraryName: String, marker: String): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return "unknown"
        val file = File(nativeDir, libraryName)
        if (!file.isFile)
            return "unknown"

        return runCatching {
            val text = String(file.readBytes(), Charsets.ISO_8859_1)
            val start = text.indexOf(marker)
            if (start < 0)
                return@runCatching "unknown"
            val maxEnd = (start + 120).coerceAtMost(text.length)
            val raw = text.substring(start, maxEnd)
            raw.takeWhile { it != '\u0000' && it != '\r' && it != '\n' }.trim()
        }.getOrDefault("unknown")
    }
}
