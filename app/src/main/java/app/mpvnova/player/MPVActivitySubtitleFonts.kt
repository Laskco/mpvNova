package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

internal val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
private val GENERIC_FONT_FAMILIES = SUBTITLE_GENERIC_FONTS.mapTo(hashSetOf()) { it.family }

internal data class SubtitleFontEntry(
    val file: File,
    val family: String?,
    val style: SubtitleFontTable.Style?,
)

private data class FontFileSnapshot(val name: String, val size: Long, val modified: Long)
private data class FontDirSnapshot(
    val dirPath: String,
    val files: List<FontFileSnapshot>,
    val entries: List<SubtitleFontEntry>,
)

@Volatile private var cachedBundledFontAssetNames: Set<String>? = null
@Volatile private var cachedSubtitleFontChoices: List<SubtitleFontChoice>? = null
@Volatile private var cachedFontDirSnapshot: FontDirSnapshot? = null
private val fontSnapshotLock = Any()

internal fun invalidateSubtitleFontChoicesCache() {
    cachedSubtitleFontChoices = null
    synchronized(fontSnapshotLock) {
        cachedFontDirSnapshot = null
    }
}

internal fun MPVActivity.subtitleFontsDir(): File = File(filesDir, "fonts").apply {
    if (!isDirectory) mkdirs()
}

internal fun MPVActivity.subtitleFontEntries(): List<SubtitleFontEntry> {
    val dir = subtitleFontsDir()
    val dirPath = dir.absolutePath
    val files = dir.listFiles { file ->
        file.isFile && file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS
    }?.sortedBy { it.name }.orEmpty()
    val signature = files.map { FontFileSnapshot(it.name, it.length(), it.lastModified()) }
    synchronized(fontSnapshotLock) {
        val cached = cachedFontDirSnapshot
        if (cached != null && cached.dirPath == dirPath && cached.files == signature) {
            return cached.entries
        }
    }
    val entries = files.map { file ->
        SubtitleFontEntry(
            file = file,
            family = SubtitleFontTable.familyName(file),
            style = SubtitleFontTable.style(file),
        )
    }
    synchronized(fontSnapshotLock) {
        cachedFontDirSnapshot = FontDirSnapshot(dirPath, signature, entries)
    }
    return entries
}

internal fun MPVActivity.subtitleFontChoices(): List<SubtitleFontChoice> {
    cachedSubtitleFontChoices?.let { return it }
    val discovered = subtitleFontEntries()
        .mapNotNullTo(linkedSetOf()) { it.family?.takeUnless(GENERIC_FONT_FAMILIES::contains) }
        .sortedFontFamilies()
        .map { SubtitleFontChoice(it, it) }
    return (SUBTITLE_GENERIC_FONTS + discovered).also { cachedSubtitleFontChoices = it }
}

internal fun subtitleFontLabel(family: String): String {
    return SUBTITLE_GENERIC_FONTS.firstOrNull { it.family == family }?.label
        ?: family.ifEmpty { SUBTITLE_GENERIC_FONTS.first().label }
}

// libass picks up the new font on the next sub-reload.
internal fun MPVActivity.importSubtitleFont(result: Int, data: Intent?): String? {
    val path = data?.getStringExtra("path")?.takeIf { result == RESULT_OK } ?: return null
    return copyFontInto(subtitleFontsDir(), path)?.let { dest ->
        invalidateSubtitleTypefaceCache()
        invalidateSubtitleFontChoicesCache()
        mpvCommand(arrayOf("sub-reload"))
        SubtitleFontTable.familyName(dest)
    }
}

private fun MPVActivity.userFontEntries(): List<SubtitleFontEntry> {
    val bundled = cachedBundledFontAssetNames
        ?: assets.list("fonts")?.toSet().orEmpty().also { cachedBundledFontAssetNames = it }
    return subtitleFontEntries().filter { it.file.name !in bundled }
}

internal fun MPVActivity.removableFontFamilies(): List<String> =
    userFontEntries()
        .mapNotNullTo(linkedSetOf()) { it.family }
        .sortedFontFamilies()

// Bundled fonts are never touched.
internal fun MPVActivity.removeSubtitleFontFamily(family: String) {
    userFontEntries()
        .filter { it.family == family }
        .forEach { it.file.delete() }
    invalidateSubtitleTypefaceCache()
    invalidateSubtitleFontChoicesCache()
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
