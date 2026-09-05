package app.mpvnova.player.preferences

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.FONT_EXTENSIONS
import app.mpvnova.player.PREF_SCREENSAVER_LOGO_URI
import app.mpvnova.player.R
import app.mpvnova.player.SubtitleFontTable
import app.mpvnova.player.UserShaderManager
import app.mpvnova.player.installSubtitleFont
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Complete, portable backup of user-owned mpvNova state. */
// Keep archive validation and its restore/rollback transaction together.
@Suppress("TooManyFunctions", "LargeClass")
object FullBackupActions {
    const val MIME_TYPE = "application/zip"

    fun suggestedFilename(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "mpvNova-backup-$stamp.zip"
    }

    fun exportWithDestinationFlow(activity: Activity) {
        val progress = activity.showSettingsProgressDialog(
            activity.getString(R.string.full_backup_exporting)
        )
        val activityRef = WeakReference(activity)
        val progressRef = WeakReference(progress)
        val appContext = activity.applicationContext
        BACKUP_IO_EXECUTOR.execute {
            val result = runCatching { createShareableBackup(appContext) }
            val current = activityRef.get() ?: return@execute
            current.runOnUiThread {
                progressRef.get()?.dismiss()
                if (current.isFinishing || current.isDestroyed) return@runOnUiThread
                result.onSuccess { backup ->
                    showFullBackupExportFlow(current, backup)
                }.onFailure {
                    Toast.makeText(
                        current,
                        R.string.full_backup_export_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun createShareableBackup(context: Context): File {
        val backupDir = File(context.cacheDir, "full-backup").apply { mkdirs() }
        backupDir.listFiles()?.forEach(File::delete)
        return File(backupDir, suggestedFilename()).also { backup ->
            ZipOutputStream(backup.outputStream().buffered()).use { writeBackup(context, it) }
        }
    }

    fun confirmImport(activity: Activity, source: Uri) {
        activity.showSettingsConfirmationDialog(
            title = activity.getString(R.string.full_backup_import_confirm_title),
            message = activity.getString(R.string.full_backup_import_confirm_message),
            confirmText = activity.getString(R.string.full_backup_import_action),
        ) {
            import(activity, source)
        }
    }

    private fun import(activity: Activity, source: Uri) {
        runWithProgress(
            activity = activity,
            messageRes = R.string.full_backup_importing,
            action = { context -> restoreBackup(context, source) },
            successRes = R.string.full_backup_imported,
            failureRes = R.string.full_backup_import_failed,
            recreateOnSuccess = true,
        )
    }

    private fun runWithProgress(
        activity: Activity,
        messageRes: Int,
        action: (Context) -> Unit,
        successRes: Int,
        failureRes: Int,
        recreateOnSuccess: Boolean = false,
    ) {
        val progress = activity.showSettingsProgressDialog(activity.getString(messageRes))
        val activityRef = WeakReference(activity)
        val progressRef = WeakReference(progress)
        val appContext = activity.applicationContext
        BACKUP_IO_EXECUTOR.execute {
            val result = runCatching { action(appContext) }
                .onFailure { Log.e("FullBackupActions", "Backup import failed", it) }
            val current = activityRef.get() ?: return@execute
            current.runOnUiThread {
                progressRef.get()?.dismiss()
                if (current.isFinishing || current.isDestroyed) return@runOnUiThread
                result.onSuccess {
                    Toast.makeText(current, successRes, Toast.LENGTH_LONG).show()
                    if (recreateOnSuccess) current.recreate()
                }.onFailure {
                    Toast.makeText(current, failureRes, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun writeBackup(context: Context, zip: ZipOutputStream) {
        zip.use {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            UserShaderManager.normalize(context)
            val screensaver = screensaverAsset(context, prefs)
            val userFonts = userFontFiles(context)
            val userShaders = userShaderFiles(context)
            val mpvConfig = File(context.filesDir, MPV_CONFIG)
            val inputConfig = File(context.filesDir, INPUT_CONFIG)

            val manifest = JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("schema", BACKUP_SCHEMA)
                .put("package", BuildConfig.APPLICATION_ID)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdAt", System.currentTimeMillis())
                .put("hasMpvConfig", mpvConfig.isFile)
                .put("hasInputConfig", inputConfig.isFile)
                .put("fonts", JSONArray(userFonts.map { "fonts/${it.name}" }))
                .put("shaders", JSONArray(userShaders.map { "shaders/${it.name}" }))
            if (screensaver != null) {
                manifest.put(
                    "screensaver",
                    JSONObject()
                        .put("path", screensaver.path)
                        .put("mimeType", screensaver.mimeType ?: "application/octet-stream"),
                )
            }

            zip.textEntry(MANIFEST_ENTRY, manifest.toString(2))
            zip.textEntry(SETTINGS_ENTRY, encodePreferences(prefs).toString(2))
            if (mpvConfig.isFile) zip.fileEntry("config/$MPV_CONFIG", mpvConfig, MAX_CONFIG_BYTES)
            if (inputConfig.isFile) zip.fileEntry("config/$INPUT_CONFIG", inputConfig, MAX_CONFIG_BYTES)
            userFonts.forEach { zip.fileEntry("fonts/${it.name}", it, MAX_FONT_BYTES) }
            userShaders.forEach { zip.fileEntry("shaders/${it.name}", it, MAX_SHADER_BYTES) }
            screensaver?.let { asset ->
                zip.putNextEntry(ZipEntry(asset.path))
                openUriInput(context, asset.uri).use { input ->
                    input.copyLimitedTo(zip, MAX_IMAGE_BYTES)
                }
                zip.closeEntry()
            }
        }
    }

    private fun restoreBackup(context: Context, source: Uri) {
        val stagingRoot = File(context.cacheDir, "backup-import-${System.nanoTime()}")
        val archive = File(stagingRoot, "backup.zip")
        val extracted = File(stagingRoot, "extracted")
        stagingRoot.mkdirs()
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                archive.outputStream().use { output -> input.copyLimitedTo(output, MAX_BACKUP_BYTES) }
            } ?: throw IOException("Could not open backup")
            extractAndValidateArchive(archive, extracted)
            val manifest = readJson(File(extracted, MANIFEST_ENTRY), MAX_JSON_BYTES)
            validateManifest(manifest)
            val restoredPrefs = decodePreferences(readJson(File(extracted, SETTINGS_ENTRY), MAX_JSON_BYTES))
            val restorePlan = validateRestorePlan(extracted, manifest, restoredPrefs)
            applyRestorePlan(context, restorePlan)
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun extractAndValidateArchive(archive: File, destination: File) {
        destination.mkdirs()
        val seen = HashSet<String>()
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                requireValidBackup(entryCount <= MAX_ARCHIVE_ENTRIES, "Too many backup entries")
                totalBytes += extractArchiveEntry(zip, entry, destination, seen)
                requireValidBackup(totalBytes <= MAX_BACKUP_BYTES, "Backup is too large")
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        requireValidBackup(
            MANIFEST_ENTRY in seen && SETTINGS_ENTRY in seen,
            "Backup metadata is missing",
        )
    }

    private fun extractArchiveEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        destination: File,
        seen: MutableSet<String>,
    ): Long {
        val name = entry.name.replace('\\', '/')
        requireValidBackup(seen.add(name) && isAllowedEntry(name), "Invalid backup entry")
        if (entry.isDirectory) return 0L
        val target = File(destination, name)
        ensureInside(destination, target)
        target.parentFile?.mkdirs()
        return FileOutputStream(target).use { output ->
            zip.copyLimitedTo(output, perEntryLimit(name))
        }
    }

    private fun validateRestorePlan(
        extracted: File,
        manifest: JSONObject,
        preferences: MutableMap<String, Any>,
    ): RestorePlan {
        val expectedEntries = linkedSetOf(MANIFEST_ENTRY, SETTINGS_ENTRY)
        val mpvConfig = optionalManifestFile(extracted, manifest.optBoolean("hasMpvConfig"), "config/$MPV_CONFIG")
        val inputConfig = optionalManifestFile(extracted, manifest.optBoolean("hasInputConfig"), "config/$INPUT_CONFIG")
        if (mpvConfig != null) expectedEntries += "config/$MPV_CONFIG"
        if (inputConfig != null) expectedEntries += "config/$INPUT_CONFIG"
        val fonts = validateFontEntries(extracted, manifest, expectedEntries)
        val shaders = validateShaderEntries(extracted, manifest, expectedEntries)
        val referencedShaders = UserShaderManager.metadataFileNames(
            preferences[UserShaderManager.PREF_ENTRIES] as? String
        )
        requireValidBackup(referencedShaders != null, "Invalid shader metadata")
        requireValidBackup(
            referencedShaders == shaders.mapTo(linkedSetOf()) { it.name },
            "Shader metadata does not match backup files",
        )
        val screensaver = validateScreensaverEntry(extracted, manifest, expectedEntries)
        validateArchiveContents(extracted, expectedEntries)
        if (screensaver == null) preferences.remove(PREF_SCREENSAVER_LOGO_URI)
        return RestorePlan(preferences, mpvConfig, inputConfig, fonts, shaders, screensaver)
    }

    private fun validateFontEntries(
        root: File,
        manifest: JSONObject,
        expectedEntries: MutableSet<String>,
    ): List<File> {
        val fonts = ArrayList<File>()
        val fontEntries = manifest.optJSONArray("fonts") ?: JSONArray()
        for (index in 0 until fontEntries.length()) {
            val path = fontEntries.getString(index)
            val extension = path.substringAfterLast('.').lowercase(Locale.ROOT)
            requireValidBackup(
                path.startsWith("fonts/") && extension in FONT_EXTENSIONS,
                "Invalid font entry",
            )
            val file = File(root, path)
            ensureInside(root, file)
            requireValidBackup(
                file.isFile && SubtitleFontTable.familyName(file) != null,
                "Invalid font file",
            )
            requireValidBackup(expectedEntries.add(path), "Duplicate font entry")
            fonts += file
        }
        return fonts
    }

    private fun validateScreensaverEntry(
        root: File,
        manifest: JSONObject,
        expectedEntries: MutableSet<String>,
    ): File? {
        val screensaver = manifest.optJSONObject("screensaver") ?: return null
        val path = screensaver.getString("path")
        requireValidBackup(path.startsWith("screensaver/"), "Invalid screensaver entry")
        val file = File(root, path)
        ensureInside(root, file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val validImage = file.isFile && file.length() in 1..MAX_IMAGE_BYTES &&
            bounds.outWidth > 0 && bounds.outHeight > 0
        requireValidBackup(validImage, "Invalid screensaver image")
        requireValidBackup(expectedEntries.add(path), "Duplicate screensaver entry")
        return file
    }

    private fun validateShaderEntries(
        root: File,
        manifest: JSONObject,
        expectedEntries: MutableSet<String>,
    ): List<File> {
        val shaders = ArrayList<File>()
        val shaderEntries = manifest.optJSONArray("shaders") ?: JSONArray()
        requireValidBackup(
            shaderEntries.length() <= UserShaderManager.MAX_SHADER_COUNT,
            "Too many shader entries",
        )
        for (index in 0 until shaderEntries.length()) {
            val path = shaderEntries.getString(index)
            requireValidBackup(path.startsWith("shaders/"), "Invalid shader entry")
            val file = File(root, path)
            ensureInside(root, file)
            requireValidBackup(UserShaderManager.validateManagedFile(file), "Invalid shader file")
            requireValidBackup(expectedEntries.add(path), "Duplicate shader entry")
            shaders += file
        }
        requireValidBackup(
            shaders.sumOf(File::length) <= UserShaderManager.MAX_TOTAL_SHADER_BYTES,
            "Shader files are too large",
        )
        return shaders
    }

    private fun validateArchiveContents(root: File, expectedEntries: Set<String>) {
        val actualEntries = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        if (actualEntries != expectedEntries) throw IOException("Backup contents do not match manifest")
    }

    private fun applyRestorePlan(context: Context, plan: RestorePlan) {
        val rollback = File(context.cacheDir, "backup-rollback-${System.nanoTime()}")
        val currentPrefs = snapshotPreferences(PreferenceManager.getDefaultSharedPreferences(context))
        var retainRollback = false
        var failure: Exception? = null
        try {
            snapshotUserFiles(context, rollback)
            File(rollback, SETTINGS_ENTRY).writeText(encodePreferences(currentPrefs).toString(2), Charsets.UTF_8)
            // Only a complete snapshot is safe to restore after live state changes.
            retainRollback = true
            replaceOptionalFile(plan.mpvConfig, File(context.filesDir, MPV_CONFIG))
            replaceOptionalFile(plan.inputConfig, File(context.filesDir, INPUT_CONFIG))
            replaceUserFonts(context, plan.fonts)
            replaceUserShaders(context, plan.shaders)

            val restoredLogo = File(context.filesDir, RESTORED_SCREENSAVER_PATH)
            if (plan.screensaver != null) {
                replaceOptionalFile(plan.screensaver, restoredLogo)
                plan.preferences[PREF_SCREENSAVER_LOGO_URI] = Uri.fromFile(restoredLogo).toString()
            } else {
                deleteRequiredFile(restoredLogo)
            }

            if (!writePreferences(PreferenceManager.getDefaultSharedPreferences(context), plan.preferences)) {
                throw IOException("Could not persist restored preferences")
            }
            retainRollback = false
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            failure = error
            if (retainRollback) {
                retainRollback = !rollbackRestorePlan(context, rollback, currentPrefs, error)
            }
            throw error
        } finally {
            if (!retainRollback) cleanupRollbackSnapshot(rollback, failure)
        }
    }

    private fun rollbackRestorePlan(
        context: Context,
        rollback: File,
        currentPrefs: Map<String, Any>,
        error: Exception,
    ): Boolean {
        val filesRestored = runCatching { restoreUserFiles(context, rollback) }
            .onFailure {
                val message = "File rollback failed; recovery data retained at $rollback"
                error.addSuppressed(IOException(message, it))
            }.isSuccess
        val preferencesRestored = runCatching {
            if (!writePreferences(PreferenceManager.getDefaultSharedPreferences(context), currentPrefs)) {
                throw IOException("Could not persist original preferences")
            }
        }.onFailure {
            val message = "Preference rollback failed; recovery data retained at $rollback"
            error.addSuppressed(IOException(message, it))
        }.isSuccess
        return filesRestored && preferencesRestored
    }

    private fun cleanupRollbackSnapshot(rollback: File, failure: Exception?) {
        runCatching {
            if (!rollback.deleteRecursively()) throw IOException("Could not clean up backup snapshot at $rollback")
        }.onFailure { cleanupError ->
            failure?.addSuppressed(cleanupError)
                ?: Log.w("FullBackupActions", "Backup snapshot cleanup failed", cleanupError)
        }
    }

    private fun snapshotUserFiles(context: Context, rollback: File) {
        rollback.mkdirs()
        listOf(MPV_CONFIG, INPUT_CONFIG).forEach { name ->
            File(context.filesDir, name).takeIf { it.isFile }?.copyTo(File(rollback, name), overwrite = true)
        }
        val fontBackup = File(rollback, "fonts")
        userFontFiles(context).forEach { file ->
            fontBackup.mkdirs()
            file.copyTo(File(fontBackup, file.name), overwrite = true)
        }
        val logo = File(context.filesDir, RESTORED_SCREENSAVER_PATH)
        if (logo.isFile) {
            val target = File(rollback, RESTORED_SCREENSAVER_PATH)
            target.parentFile?.mkdirs()
            logo.copyTo(target, overwrite = true)
        }
        val shaderBackup = File(rollback, "shaders")
        userShaderFiles(context).forEach { file ->
            shaderBackup.mkdirs()
            file.copyTo(File(shaderBackup, file.name), overwrite = true)
        }
    }

    private fun restoreUserFiles(context: Context, rollback: File) {
        replaceOptionalFile(File(rollback, MPV_CONFIG).takeIf { it.isFile }, File(context.filesDir, MPV_CONFIG))
        replaceOptionalFile(File(rollback, INPUT_CONFIG).takeIf { it.isFile }, File(context.filesDir, INPUT_CONFIG))
        replaceUserFonts(context, File(rollback, "fonts").listFiles()?.filter { it.isFile }.orEmpty())
        replaceUserShaders(context, File(rollback, "shaders").listFiles()?.filter { it.isFile }.orEmpty())
        replaceOptionalFile(
            File(rollback, RESTORED_SCREENSAVER_PATH).takeIf { it.isFile },
            File(context.filesDir, RESTORED_SCREENSAVER_PATH),
        )
    }

    private fun replaceUserFonts(context: Context, fonts: List<File>) {
        userFontFiles(context).forEach(::deleteRequiredFile)
        val targetDir = File(context.filesDir, "fonts").apply { mkdirs() }
        fonts.forEach { source ->
            val target = File(targetDir, safeFilename(source.name))
            source.inputStream().use { input ->
                if (installSubtitleFont(input, target) == null) throw IOException("Could not restore font")
            }
        }
    }

    private fun replaceUserShaders(context: Context, shaders: List<File>) {
        val targetDir = UserShaderManager.directory(context)
        targetDir.listFiles()?.filter { it.isFile }?.forEach(::deleteRequiredFile)
        shaders.forEach { source ->
            requireValidBackup(UserShaderManager.validateManagedFile(source), "Invalid shader file")
            source.copyTo(File(targetDir, safeFilename(source.name)), overwrite = true)
        }
        UserShaderManager.invalidateCache()
    }

    private fun replaceOptionalFile(source: File?, target: File) {
        if (source == null) {
            deleteRequiredFile(target)
            return
        }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.restore")
        source.copyTo(temporary, overwrite = true)
        if ((!target.exists() || target.delete()) && temporary.renameTo(target)) return
        temporary.delete()
        throw IOException("Could not replace ${target.name}")
    }

    private fun deleteRequiredFile(target: File) {
        if (target.exists() && !target.delete()) throw IOException("Could not delete ${target.absolutePath}")
    }

    private fun screensaverAsset(context: Context, prefs: SharedPreferences): ScreensaverAsset? {
        val raw = prefs.getString(PREF_SCREENSAVER_LOGO_URI, null)?.takeIf { it.isNotBlank() } ?: return null
        val uri = Uri.parse(raw)
        val displayName = uriDisplayName(context, uri)
        val extension = displayName?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in IMAGE_EXTENSIONS }
            ?: "img"
        return ScreensaverAsset(
            uri = uri,
            path = "screensaver/logo.$extension",
            mimeType = context.contentResolver.getType(uri),
        )
    }

    private fun openUriInput(context: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") return File(requireNotNull(uri.path)).inputStream()
        return context.contentResolver.openInputStream(uri) ?: throw IOException("Could not read screensaver image")
    }

    private fun uriDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        var displayName: String? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) displayName = cursor.getString(0)
        }
        return displayName ?: uri.lastPathSegment
    }

    private fun userFontFiles(context: Context): List<File> {
        val bundled = context.assets.list("fonts")?.toSet().orEmpty()
        return File(context.filesDir, "fonts")
            .listFiles { file ->
                file.isFile && file.name !in bundled && file.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS
            }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()
    }

    private fun userShaderFiles(context: Context): List<File> {
        val root = UserShaderManager.directory(context)
        return UserShaderManager.shaders(context).mapNotNull { shader ->
            File(root, shader.fileName).takeIf(UserShaderManager::validateManagedFile)
        }
    }

    private fun encodePreferences(prefs: SharedPreferences): JSONObject =
        encodePreferences(prefs.all.filterKeys { it != UserShaderManager.PREF_FOLDER_URIS })

    private fun encodePreferences(preferences: Map<String, *>): JSONObject {
        val values = JSONObject()
        preferences.toSortedMap().forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is String -> entry.put("type", "string").put("value", value)
                is Set<*> -> entry.put("type", "stringSet").put(
                    "value",
                    JSONArray(value.filterIsInstance<String>().sorted()),
                )
                else -> throw IOException("Unsupported preference type for $key")
            }
            values.put(key, entry)
        }
        return JSONObject().put("schema", SETTINGS_SCHEMA).put("values", values)
    }

    private fun decodePreferences(json: JSONObject): MutableMap<String, Any> {
        if (json.optInt("schema", -1) != SETTINGS_SCHEMA) throw IOException("Unsupported settings schema")
        val values = json.getJSONObject("values")
        val result = LinkedHashMap<String, Any>()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = values.getJSONObject(key)
            result[key] = when (entry.getString("type")) {
                "boolean" -> entry.getBoolean("value")
                "int" -> entry.getInt("value")
                "long" -> entry.getLong("value")
                "float" -> entry.getDouble("value").toFloat()
                "string" -> entry.getString("value")
                "stringSet" -> entry.getJSONArray("value").toStringSet()
                else -> throw IOException("Unsupported preference type")
            }
        }
        return result
    }

    private fun snapshotPreferences(prefs: SharedPreferences): MutableMap<String, Any> =
        prefs.all.mapValuesTo(LinkedHashMap()) { (_, value) ->
            when (value) {
                is Set<*> -> value.filterIsInstance<String>().toSet()
                null -> throw IOException("Preference value is missing")
                else -> value
            }
        }

    private fun writePreferences(prefs: SharedPreferences, values: Map<String, Any>): Boolean {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> throw IOException("Unsupported restored preference type")
            }
        }
        return editor.commit()
    }

    private fun validateManifest(manifest: JSONObject) {
        val schema = manifest.optInt("schema", -1)
        if (manifest.optString("format") != BACKUP_FORMAT || schema !in MIN_BACKUP_SCHEMA..BACKUP_SCHEMA) {
            throw IOException("Unsupported backup format")
        }
    }

    private fun optionalManifestFile(root: File, present: Boolean, path: String): File? {
        val file = File(root, path)
        ensureInside(root, file)
        if (!present) {
            if (file.exists()) throw IOException("Unexpected backup entry")
            return null
        }
        if (!file.isFile) throw IOException("Backup entry is missing")
        return file
    }

    private fun readJson(file: File, maxBytes: Long): JSONObject {
        if (!file.isFile || file.length() !in 1..maxBytes) throw IOException("Invalid backup metadata")
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun isAllowedEntry(name: String): Boolean = when {
        name == MANIFEST_ENTRY || name == SETTINGS_ENTRY -> true
        name == "config/$MPV_CONFIG" || name == "config/$INPUT_CONFIG" -> true
        name.startsWith("fonts/") -> isSafeNestedEntry(name)
        name.startsWith("shaders/") -> isSafeNestedEntry(name)
        name.startsWith("screensaver/") -> isSafeNestedEntry(name)
        else -> false
    }

    private fun isSafeNestedEntry(name: String): Boolean {
        val filename = name.substringAfter('/')
        return name.count { it == '/' } == 1 && safeFilename(filename) == filename
    }

    private fun perEntryLimit(name: String): Long = when {
        name == MANIFEST_ENTRY || name == SETTINGS_ENTRY -> MAX_JSON_BYTES
        name.startsWith("config/") -> MAX_CONFIG_BYTES
        name.startsWith("fonts/") -> MAX_FONT_BYTES
        name.startsWith("shaders/") -> MAX_SHADER_BYTES
        name.startsWith("screensaver/") -> MAX_IMAGE_BYTES
        else -> 0L
    }

    private fun ensureInside(root: File, file: File) {
        val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        if (!file.canonicalPath.startsWith(rootPath)) throw IOException("Unsafe backup path")
    }

    private fun requireValidBackup(valid: Boolean, message: String) {
        if (!valid) throw IOException(message)
    }

    private fun safeFilename(name: String): String =
        name.substringAfterLast('/').replace(UNSAFE_FILENAME, "_")

    private fun JSONArray.toStringSet(): Set<String> {
        val result = LinkedHashSet<String>()
        for (index in 0 until length()) result += getString(index)
        return result
    }

    private fun ZipOutputStream.textEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.fileEntry(name: String, source: File, limit: Long) {
        putNextEntry(ZipEntry(name))
        source.inputStream().use { it.copyLimitedTo(this, limit) }
        closeEntry()
    }

    private fun InputStream.copyLimitedTo(output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) throw IOException("Backup entry is too large")
            output.write(buffer, 0, read)
        }
    }

    private data class ScreensaverAsset(val uri: Uri, val path: String, val mimeType: String?)
    private data class RestorePlan(
        val preferences: MutableMap<String, Any>,
        val mpvConfig: File?,
        val inputConfig: File?,
        val fonts: List<File>,
        val shaders: List<File>,
        val screensaver: File?,
    )

    private const val BACKUP_FORMAT = "mpvnova-full-backup"
    private const val MIN_BACKUP_SCHEMA = 1
    private const val BACKUP_SCHEMA = 2
    private const val SETTINGS_SCHEMA = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val SETTINGS_ENTRY = "settings.json"
    private const val MPV_CONFIG = "mpv.conf"
    private const val INPUT_CONFIG = "input.conf"
    private const val RESTORED_SCREENSAVER_PATH = "user-assets/screensaver-logo"
    private const val MAX_ARCHIVE_ENTRIES = 256
    private const val MAX_BACKUP_BYTES = 128L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 4L * 1024L * 1024L
    private const val MAX_CONFIG_BYTES = 2L * 1024L * 1024L
    private const val MAX_FONT_BYTES = SubtitleFontTable.MAX_FONT_FILE_BYTES
    private const val MAX_SHADER_BYTES = UserShaderManager.MAX_SHADER_BYTES
    private const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")
    private val BACKUP_IO_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mpvNova-backup-io")
    }
}
