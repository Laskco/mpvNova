package app.mpvnova.player

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException

internal object TmdbSettings {
    const val ENABLED = "tmdb_title_lookup"
    const val TOKEN_ACTION = "tmdb_read_token"
    private var cachedToken: String? = null
    private var verification: TmdbKeyVerification? = null
    private const val MAX_TOKEN_LENGTH = 8192
    private val BEARER_PREFIX = Regex("^Bearer\\s+", RegexOption.IGNORE_CASE)
    // RFC 6750 bearer credentials can also contain ~, +, / and trailing = padding.
    private val BEARER_TOKEN = Regex("[A-Za-z0-9._~+/-]+=*")
    private val API_KEY = Regex("[a-fA-F0-9]{32}")

    fun isApiKey(value: String): Boolean = API_KEY.matches(value)

    fun validApiKeyInput(value: String): Boolean = value.isEmpty() || isApiKey(value)

    @Synchronized
    fun verificationResult(): TmdbKeyVerification? = verification

    @Synchronized
    fun recordVerification(context: Context, key: String, result: TmdbKeyVerification): Boolean {
        val current = key.isNotEmpty() && token(context) == key
        if (current) {
            verification = result
            try {
                TmdbVerificationStore(context).write(key, result)
            } catch (_: IOException) {
                Log.w("mpv-tmdb", "Could not persist key verification status")
            }
        }
        return current
    }

    @Synchronized
    fun token(context: Context): String {
        cachedToken?.let { return it }
        val token = try {
            tokenFile(context).openRead().bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            ""
        }
        cachedToken = token
        verification = TmdbVerificationStore(context).read(token)
        return token
    }

    fun normalizeToken(value: String): String = value.trim()
        .removeSurrounding("\"").removeSurrounding("'")
        .replaceFirst(BEARER_PREFIX, "")
        .filterNot { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }

    fun validToken(value: String): Boolean = value.isEmpty() ||
        (value.length <= MAX_TOKEN_LENGTH && BEARER_TOKEN.matches(value))

    @Synchronized
    fun saveToken(context: Context, value: String) {
        require(validToken(value))
        val file = tokenFile(context)
        if (value.isEmpty()) {
            file.delete()
        } else {
            val stream = file.startWrite()
            try {
                stream.write(value.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: IOException) {
                file.failWrite(stream)
                throw error
            }
        }
        cachedToken = value
        verification = null
        TmdbVerificationStore(context).clear()
    }

    // Separate from preferences/config files collected by backups and support bundles.
    private fun tokenFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, "tmdb-token"))
}
