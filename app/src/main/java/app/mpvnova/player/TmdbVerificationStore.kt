package app.mpvnova.player

import android.content.Context
import android.util.AtomicFile
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal class TmdbVerificationStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "tmdb-verification.json"))

    fun read(key: String): TmdbKeyVerification? = try {
        val data = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
        if (key.isNotEmpty() && data.optString("credentialHash") == fingerprint(key)) {
            TmdbKeyVerification.entries.firstOrNull { it.name == data.optString("result") }
        } else null
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    fun write(key: String, result: TmdbKeyVerification) {
        val data = JSONObject().put("credentialHash", fingerprint(key)).put("result", result.name)
        val stream = file.startWrite()
        try {
            stream.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: IOException) {
            file.failWrite(stream)
            throw error
        }
    }

    fun clear() = file.delete()

    private fun fingerprint(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
