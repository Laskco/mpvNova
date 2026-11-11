package app.mpvnova.player.preferences

import java.io.DataInputStream
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

private const val METADATA_DIR = "assets/updater_dm/"
private const val MAX_INDEX_BYTES = 4096
private const val MAX_METADATA_BYTES = 4 * 1024 * 1024
private val metadataPath = Regex("""\d+/\.dm""")

internal fun readUpdateDexMetadata(apk: File, sdk: Int): ByteArray? = ZipFile(apk).use { zip ->
    val index = zip.readBoundedEntry("dex-metadata-map.properties", MAX_INDEX_BYTES) ?: return null
    val properties = Properties().apply { index.inputStream().use(::load) }
    val maxListedSdk = properties.stringPropertyNames().mapNotNull(String::toIntOrNull)
        .filter { it != Int.MAX_VALUE }.maxOrNull() ?: return null
    val key = if (sdk > maxListedSdk) Int.MAX_VALUE else sdk
    val path = properties.getProperty(key.toString())?.takeIf(metadataPath::matches) ?: return null
    zip.readBoundedEntry(path.substringBefore('/') + ".dm", MAX_METADATA_BYTES)
}

private fun ZipFile.readBoundedEntry(path: String, maxBytes: Int): ByteArray? {
    val entry = getEntry(METADATA_DIR + path)?.takeIf { it.size in 1L..maxBytes.toLong() } ?: return null
    return DataInputStream(getInputStream(entry)).use { stream ->
        val bytes = ByteArray(entry.size.toInt())
        stream.readFully(bytes)
        bytes.takeIf { stream.read() == -1 }
    }
}
