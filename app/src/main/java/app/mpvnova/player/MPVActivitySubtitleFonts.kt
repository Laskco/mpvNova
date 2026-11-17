package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

internal val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

internal fun MPVActivity.subtitleFontsDir(): File = File(filesDir, "fonts").apply { mkdirs() }

internal fun MPVActivity.subtitleFontChoices(): List<SubtitleFontChoice> {
    val genericFamilies = SUBTITLE_GENERIC_FONTS.mapTo(mutableSetOf()) { it.family }
    val discovered = subtitleFontsDir()
        .listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS }
        ?.mapNotNull { SubtitleFontTable.familyName(it) }
        ?.filter { it !in genericFamilies }
        ?.distinct()
        ?.sortedBy { it.lowercase(Locale.ROOT) }
        .orEmpty()
        .map { SubtitleFontChoice(it, it) }
    return SUBTITLE_GENERIC_FONTS + discovered
}

internal fun subtitleFontLabel(family: String): String {
    return SUBTITLE_GENERIC_FONTS.firstOrNull { it.family == family }?.label
        ?: family.ifEmpty { SUBTITLE_GENERIC_FONTS.first().label }
}

// libass picks up the new font on the next sub-reload.
internal fun MPVActivity.importSubtitleFont(result: Int, data: Intent?): String? {
    val path = data?.getStringExtra("path")?.takeIf { result == RESULT_OK } ?: return null
    return copyFontInto(subtitleFontsDir(), path)?.let { dest ->
        mpvCommand(arrayOf("sub-reload"))
        SubtitleFontTable.familyName(dest)
    }
}

private fun MPVActivity.userFontFiles(): List<File> {
    val bundled = assets.list("fonts")?.toSet().orEmpty()
    return subtitleFontsDir()
        .listFiles { file ->
            file.isFile &&
                file.name !in bundled &&
                file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS
        }
        ?.toList()
        .orEmpty()
}

internal fun MPVActivity.removableFontFamilies(): List<String> =
    userFontFiles()
        .mapNotNull { SubtitleFontTable.familyName(it) }
        .distinct()
        .sortedBy { it.lowercase(Locale.ROOT) }

// Bundled fonts are never touched.
internal fun MPVActivity.removeSubtitleFontFamily(family: String) {
    userFontFiles()
        .filter { SubtitleFontTable.familyName(it) == family }
        .forEach { it.delete() }
    if (subStyleFontFamily == family) {
        subStyleFontFamily = SUBTITLE_FONT_DEFAULT_FAMILY
        if (customSubStyleEnabled)
            applyCustomSubtitleStyle()
        writeSubtitleStyleSettings()
    }
    mpvCommand(arrayOf("sub-reload"))
}

private fun MPVActivity.copyFontInto(dir: File, path: String): File? = runCatching {
    if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        val name = fontDisplayName(uri) ?: return@runCatching null
        if (!isFontFileName(name)) return@runCatching null
        val dest = File(dir, sanitizeFontFileName(name))
        contentResolver.openInputStream(uri)?.use { input ->
            installSubtitleFont(input, dest)
        } ?: return@runCatching null
        dest
    } else {
        val src = File(path)
        if (!isFontFileName(src.name) || !src.canRead() ||
            src.length() !in 1..SubtitleFontTable.MAX_FONT_FILE_BYTES
        ) return@runCatching null
        val dest = File(dir, sanitizeFontFileName(src.name))
        src.inputStream().use { input -> installSubtitleFont(input, dest) }
    }
}.getOrNull()

private fun MPVActivity.fontDisplayName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.columnCount > 0) {
            cursor.getString(0)?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

private fun isFontFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.ROOT) in FONT_EXTENSIONS

private val FONT_NAME_UNSAFE = Regex("[^A-Za-z0-9._-]")

private fun sanitizeFontFileName(name: String): String =
    name.substringAfterLast('/').replace(FONT_NAME_UNSAFE, "_")
