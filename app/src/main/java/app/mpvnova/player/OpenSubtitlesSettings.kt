package app.mpvnova.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Suppress("TooManyFunctions") // All methods belong to the same credential and preference lifecycle.
internal object OpenSubtitlesSettings {
    const val ENABLED = "opensubtitles_enabled"
    private const val LANGUAGE = "opensubtitles_language"
    private const val KEY_ALIAS = "mpvnova-opensubtitles-v1"
    private const val MAX_AUTH_BYTES = 16384
    private const val AUTH_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val MIN_KEY_LENGTH = 8
    private const val MAX_KEY_LENGTH = 512
    private const val FIRST_HEADER_CHARACTER = 33
    private const val LAST_HEADER_CHARACTER = 126

    private class CachedAuth(
        val path: String,
        val state: JSONObject?,
        val encrypted: ByteArray,
    )

    private class DecodedAuth(val state: JSONObject, val encrypted: ByteArray)

    private var cachedAuth: CachedAuth? = null

    fun isAvailable(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false) &&
            credentials(context) != null

    fun language(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(LANGUAGE, "en") ?: "en"

    fun setLanguage(context: Context, value: String) {
        if (OPEN_SUBTITLES_PREFERENCE_LANGUAGE.matches(value))
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(LANGUAGE, value).apply()
    }

    fun validApiKey(value: String): Boolean = value.length in MIN_KEY_LENGTH..MAX_KEY_LENGTH &&
        value.all { it.code in FIRST_HEADER_CHARACTER..LAST_HEADER_CHARACTER }

    fun credentials(context: Context): OpenSubtitlesCredentials? = read(context)?.let { state ->
        val key = state.optString("key")
        if (validApiKey(key)) OpenSubtitlesCredentials(
            key, state.optString("token"), state.optString("host", "api.opensubtitles.com"),
        )
        else null
    }

    fun username(context: Context): String = read(context)?.optString("username").orEmpty()

    fun isKeyVerified(context: Context): Boolean = read(context)?.optBoolean("key_verified", false) == true

    fun saveVerifiedKey(context: Context, key: String) {
        require(validApiKey(key))
        val state = read(context)?.takeIf { it.optString("key") == key }?.let { JSONObject(it.toString()) }
            ?: JSONObject().put("key", key).put("host", "api.opensubtitles.com")
        write(context, state.put("key_verified", true))
    }

    fun saveSession(context: Context, session: OpenSubtitlesSession) {
        write(context, JSONObject().put("key", session.credentials.apiKey).put("token", session.credentials.token)
            .put("host", session.credentials.apiHost).put("username", session.username).put("key_verified", true))
    }

    fun clearSession(context: Context) {
        credentials(context)?.let {
            write(context, JSONObject().put("key", it.apiKey).put("host", "api.opensubtitles.com")
                .put("key_verified", isKeyVerified(context)))
        }
    }

    @Synchronized
    fun clear(context: Context) {
        cachedAuth = null
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(ENABLED, false).apply()
        val file = authFile(context)
        file.delete()
        val base = file.baseFile
        if (listOf(base, File(base.path + ".bak"), File(base.path + ".new")).any { it.exists() })
            throw IOException("Could not remove OpenSubtitles credentials")
    }

    @Synchronized
    private fun read(context: Context): JSONObject? {
        val file = authFile(context)
        val base = file.baseFile
        val cached = validCachedAuth(file)
        if (cached != null) return cached.state
        val decoded = readUncached(file)
        // Keep transient keystore/decryption failures retryable. Missing files can be memoized.
        cachedAuth = when {
            decoded != null -> CachedAuth(base.absolutePath, decoded.state, decoded.encrypted)
            !base.exists() -> CachedAuth(base.absolutePath, null, ByteArray(0))
            else -> null
        }
        return decoded?.state
    }

    private fun validCachedAuth(file: AtomicFile): CachedAuth? {
        // The file lives in app-private noBackupFilesDir and all mutations go through write()/clear().
        return cachedAuth?.takeIf { it.path == file.baseFile.absolutePath }
    }

    private fun readUncached(file: AtomicFile): DecodedAuth? = try {
        file.openRead().use { stream ->
            val buffer = ByteArray(MAX_AUTH_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            val bytes = buffer.copyOf(size)
            if (bytes.size !in (IV_BYTES + 1)..MAX_AUTH_BYTES || bytes[0].toInt() != 1) null
            else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    GCMParameterSpec(AUTH_TAG_BITS, bytes.copyOfRange(1, IV_BYTES + 1)))
                DecodedAuth(
                    JSONObject(String(cipher.doFinal(bytes, IV_BYTES + 1, bytes.size - IV_BYTES - 1), Charsets.UTF_8)),
                    bytes,
                )
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: org.json.JSONException) {
        null
    }

    @Synchronized
    private fun write(context: Context, state: JSONObject) {
        cachedAuth = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(state.toString().toByteArray(Charsets.UTF_8))
            requireAuthSize(bytes)
            val file = authFile(context)
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: IOException) {
                file.failWrite(stream)
                throw error
            }
            verifyWritten(file, bytes)
            val base = file.baseFile
            cachedAuth = CachedAuth(base.absolutePath, state, bytes)
        } catch (error: GeneralSecurityException) {
            throw IOException("Could not save OpenSubtitles credentials", error)
        }
    }

    private fun requireAuthSize(bytes: ByteArray) {
        if (bytes.size > MAX_AUTH_BYTES) throw IOException("Could not save OpenSubtitles credentials")
    }

    private fun verifyWritten(file: AtomicFile, expected: ByteArray) {
        // AtomicFile logs some sync/rename failures instead of throwing them.
        file.openRead().use { input ->
            val saved = ByteArray(expected.size)
            DataInputStream(input).readFully(saved)
            if (!saved.contentEquals(expected) || input.read() != -1)
                throw IOException("Could not save OpenSubtitles credentials")
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun authFile(context: Context) = AtomicFile(File(context.noBackupFilesDir, "opensubtitles-auth"))
}
