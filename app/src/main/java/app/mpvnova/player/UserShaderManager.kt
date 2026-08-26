package app.mpvnova.player

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

internal data class UserShader(
    val id: String,
    val displayName: String,
    val fileName: String,
    val enabled: Boolean,
)

internal data class ShaderImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
)

internal data class ShaderFolderRefreshResult(
    val imported: Int,
    val skipped: Int,
    val foldersScanned: Int,
    val foldersUnavailable: Int,
    val errors: List<String>,
)

@Suppress("TooManyFunctions")
internal object UserShaderManager {
    const val PREF_ENABLED = "shader_manager_enabled"
    const val PREF_ENTRIES = "shader_manager_entries"
    const val PREF_FOLDER_URIS = "shader_manager_folder_uris"
    const val DIRECTORY = "shaders/user"
    const val MAX_SHADER_BYTES = 8L * 1024L * 1024L
    const val MAX_TOTAL_SHADER_BYTES = 32L * 1024L * 1024L
    const val MAX_SHADER_COUNT = 64
    private const val SCHEMA = 1
    private val extensions = setOf("glsl", "hook", "comp")
    private val lock = Any()
    private var cachedRaw: String? = null
    private var cachedEntries: List<UserShader> = emptyList()
    private var cacheInitialized = false

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
    }

    fun shaders(context: Context): List<UserShader> = synchronized(lock) {
        load(context)
    }

    fun shaderUrisInTree(context: Context, treeUri: Uri): List<Uri> = runCatching {
        queryShaderUrisInTree(context, treeUri)
    }.getOrDefault(emptyList())

    fun rememberFolder(context: Context, treeUri: Uri) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val folders = prefs.getStringSet(PREF_FOLDER_URIS, emptySet()).orEmpty().toMutableSet()
        if (folders.add(treeUri.toString())) {
            prefs.edit().putStringSet(PREF_FOLDER_URIS, folders).apply()
        }
    }

    fun hasRememberedFolders(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(PREF_FOLDER_URIS, emptySet())
            .orEmpty()
            .isNotEmpty()

    fun refreshFolders(context: Context): ShaderFolderRefreshResult {
        val folders = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(PREF_FOLDER_URIS, emptySet())
            .orEmpty()
            .mapNotNull { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
        val uris = mutableListOf<Uri>()
        val errors = mutableListOf<String>()
        var scanned = 0
        var unavailable = 0
        folders.forEach { folder ->
            runCatching { queryShaderUrisInTree(context, folder) }
                .onSuccess { found ->
                    scanned++
                    uris += found
                }
                .onFailure { error ->
                    unavailable++
                    errors += error.message ?: folder.toString()
                }
        }
        val imported = import(context, uris)
        return ShaderFolderRefreshResult(
            imported = imported.imported,
            skipped = imported.skipped,
            foldersScanned = scanned,
            foldersUnavailable = unavailable,
            errors = (errors + imported.errors).distinct(),
        )
    }

    private fun queryShaderUrisInTree(context: Context, treeUri: Uri): List<Uri> {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return buildList {
            val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
                ?: throw IOException("Could not read shader folder")
            cursor.use {
                val idColumn = cursor.getColumnIndexOrThrow(columns[0])
                val nameColumn = cursor.getColumnIndexOrThrow(columns[1])
                val mimeColumn = cursor.getColumnIndexOrThrow(columns[2])
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeColumn)
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR && extension in extensions) {
                        add(DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn)))
                    }
                }
            }
        }
    }

    fun normalize(context: Context) = synchronized(lock) {
        save(context, load(context))
    }

    fun invalidateCache() = synchronized(lock) {
        cacheInitialized = false
        cachedRaw = null
        cachedEntries = emptyList()
    }

    fun metadataFileNames(raw: String?): Set<String>? {
        if (raw == null) return emptySet()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != SCHEMA) return@runCatching null
            val entries = json.optJSONArray("entries") ?: JSONArray()
            if (entries.length() > MAX_SHADER_COUNT) return@runCatching null
            val ids = HashSet<String>()
            val files = LinkedHashSet<String>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: return@runCatching null
                val id = entry.optString("id")
                val fileName = entry.optString("fileName")
                val validId = id.isNotBlank() && ids.add(id)
                val validFile = isSafeFileName(fileName) && files.add(fileName)
                if (!validId || !validFile) {
                    return@runCatching null
                }
            }
            files
        }.getOrNull()
    }

    fun enabledPaths(context: Context): List<String> = synchronized(lock) {
        if (!isEnabled(context)) return@synchronized emptyList()
        val root = directory(context)
        load(context)
            .filter { it.enabled }
            .mapNotNull { shader ->
                File(root, shader.fileName).takeIf { it.isFile }?.absolutePath
            }
    }

    fun setShaderEnabled(context: Context, id: String, enabled: Boolean) = synchronized(lock) {
        update(context) { entries ->
            entries.map { shader ->
                if (shader.id == id) shader.copy(enabled = enabled) else shader
            }
        }
    }

    fun move(context: Context, id: String, offset: Int) = synchronized(lock) {
        update(context) { entries ->
            val from = entries.indexOfFirst { it.id == id }
            if (from < 0) return@update entries
            val to = (from + offset).coerceIn(0, entries.lastIndex)
            if (from == to) return@update entries
            entries.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun disableAll(context: Context) = synchronized(lock) {
        update(context) { entries -> entries.map { it.copy(enabled = false) } }
    }

    fun remove(context: Context, id: String) = synchronized(lock) {
        val entries = load(context)
        val removed = entries.firstOrNull { it.id == id } ?: return@synchronized
        File(directory(context), removed.fileName).delete()
        save(context, entries.filterNot { it.id == id })
    }

    @Throws(IOException::class)
    fun import(context: Context, uris: List<Uri>): ShaderImportResult = synchronized(lock) {
        val entries = load(context).toMutableList()
        val root = directory(context)
        val existingHashes = entries.mapNotNull { shaderHash(root, it) }.toMutableSet()
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        var totalBytes = entries.sumOf { shader ->
            File(root, shader.fileName).takeIf { it.isFile }?.length() ?: 0L
        }

        uris.distinct().forEach { uri ->
            if (entries.size >= MAX_SHADER_COUNT) {
                errors += context.getString(R.string.shader_import_limit_reached, MAX_SHADER_COUNT)
                return@forEach
            }
            runCatching {
                val displayName = displayName(context, uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.shader_unnamed)
                val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension !in extensions) {
                    errors += context.getString(R.string.shader_import_unsupported, displayName)
                    return@runCatching
                }
                val bytes = readShader(context, uri)
                if (totalBytes + bytes.size > MAX_TOTAL_SHADER_BYTES) {
                    throw IOException(context.getString(R.string.shader_import_total_too_large))
                }
                val hash = bytes.sha256()
                if (hash in existingHashes) {
                    skipped++
                    return@runCatching
                }
                validateText(bytes, displayName)
                val id = UUID.randomUUID().toString()
                val fileName = "$id.$extension"
                val target = File(root, fileName)
                writeAtomically(target, bytes)
                entries += UserShader(
                    id = id,
                    displayName = displayName.take(MAX_DISPLAY_NAME_LENGTH),
                    fileName = fileName,
                    enabled = false,
                )
                existingHashes += hash
                totalBytes += bytes.size
                imported++
            }.onFailure { error ->
                errors += error.message ?: context.getString(R.string.shader_import_failed)
            }
        }
        save(context, entries)
        ShaderImportResult(imported, skipped, errors.distinct())
    }

    fun validateManagedFile(file: File): Boolean {
        val validFile = file.isFile && file.length() in 1..MAX_SHADER_BYTES
        val validExtension = file.extension.lowercase(Locale.ROOT) in extensions
        return validFile && validExtension &&
            runCatching { validateText(file.readBytes(), file.name) }.isSuccess
    }

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun update(context: Context, transform: (List<UserShader>) -> List<UserShader>) {
        save(context, transform(load(context)))
    }

    private fun load(context: Context): List<UserShader> {
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_ENTRIES, null)
        return when {
            cacheInitialized && raw == cachedRaw -> cachedEntries
            raw == null -> emptyList<UserShader>().also { entries ->
                cachedRaw = null
                cachedEntries = entries
                cacheInitialized = true
            }
            else -> {
                val root = directory(context)
                val result = runCatching {
                    val json = JSONObject(raw)
                    if (json.optInt("schema", -1) != SCHEMA) return@runCatching emptyList()
                    val array = json.optJSONArray("entries") ?: JSONArray()
                    buildList {
                        for (index in 0 until array.length()) {
                            parseEntry(root, array.optJSONObject(index))?.let(::add)
                        }
                    }
                }.getOrDefault(emptyList())
                result.distinctBy { it.id }.also { entries ->
                    cachedRaw = raw
                    cachedEntries = entries
                    cacheInitialized = true
                }
            }
        }
    }

    private fun save(context: Context, entries: List<UserShader>) {
        val array = JSONArray()
        entries.forEach { shader ->
            array.put(
                JSONObject()
                    .put("id", shader.id)
                    .put("displayName", shader.displayName)
                    .put("fileName", shader.fileName)
                    .put("enabled", shader.enabled)
            )
        }
        val raw = JSONObject().put("schema", SCHEMA).put("entries", array).toString()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_ENTRIES, raw)
            .apply()
        cachedRaw = raw
        cachedEntries = entries.toList()
        cacheInitialized = true
    }

    private fun shaderHash(root: File, shader: UserShader): String? =
        runCatching { File(root, shader.fileName).readBytes().sha256() }.getOrNull()

    private fun readShader(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException(context.getString(R.string.shader_import_open_failed))
        return stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                requireImportSize(context, total, complete = false)
                output.write(buffer, 0, read)
            }
            requireImportSize(context, total, complete = true)
            output.toByteArray()
        }
    }

    private fun requireImportSize(context: Context, size: Long, complete: Boolean) {
        if (size > MAX_SHADER_BYTES) {
            throw IOException(context.getString(R.string.shader_import_too_large))
        }
        if (complete && size == 0L) {
            throw IOException(context.getString(R.string.shader_import_empty))
        }
    }

    private fun validateText(bytes: ByteArray, name: String) {
        if (bytes.any { it == 0.toByte() }) throw IOException("$name is not a text shader")
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.import")
        temporary.outputStream().use { it.write(bytes) }
        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not store shader")
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        return name ?: uri.lastPathSegment
    }

    private fun isSafeManagedFile(root: File, file: File, fileName: String): Boolean =
        isSafeFileName(fileName) &&
            file.canonicalFile.parentFile == root.canonicalFile

    private fun parseEntry(root: File, item: JSONObject?): UserShader? {
        return item?.let { entry ->
            val id = entry.optString("id")
            val displayName = entry.optString("displayName")
            val fileName = entry.optString("fileName")
            val file = File(root, fileName)
            if (id.isBlank() || !isSafeManagedFile(root, file, fileName) || !file.isFile) {
                null
            } else {
                UserShader(id, displayName, fileName, entry.optBoolean("enabled", false))
            }
        }
    }

    private fun isSafeFileName(fileName: String): Boolean =
        fileName.isNotBlank() &&
            fileName == File(fileName).name &&
            File(fileName).extension.lowercase(Locale.ROOT) in extensions

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private const val MAX_DISPLAY_NAME_LENGTH = 160
}
