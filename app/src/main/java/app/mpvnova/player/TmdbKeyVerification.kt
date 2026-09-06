package app.mpvnova.player

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection

internal enum class TmdbKeyVerification { VERIFIED, REJECTED, UNAVAILABLE }

internal class TmdbKeyVerifier(private val request: (String, String) -> JSONObject = ::requestTmdbJson) {
    fun verify(key: String): TmdbKeyVerification = try {
        val response = request("authentication", key)
        when (response.opt("success")) {
            true -> TmdbKeyVerification.VERIFIED
            false -> TmdbKeyVerification.REJECTED
            else -> TmdbKeyVerification.UNAVAILABLE
        }
    } catch (error: TmdbHttpException) {
        if (error.status == HttpURLConnection.HTTP_UNAUTHORIZED) TmdbKeyVerification.REJECTED
        else TmdbKeyVerification.UNAVAILABLE
    } catch (_: IOException) {
        TmdbKeyVerification.UNAVAILABLE
    } catch (_: JSONException) {
        TmdbKeyVerification.UNAVAILABLE
    }
}
