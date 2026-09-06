package app.mpvnova.player.preferences

import android.text.InputType
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.mpvnova.player.R
import app.mpvnova.player.TmdbSettings
import app.mpvnova.player.TmdbKeyVerifier
import app.mpvnova.player.TmdbKeyVerification
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun PreferenceFragmentCompat.bindTmdbPreferences() {
    val tokenPreference = findPreference<Preference>(TmdbSettings.TOKEN_ACTION) ?: return
    val verification = TmdbPreferenceVerification(this, tokenPreference)
    lifecycle.addObserver(verification)
    verification.restoreOrVerify()
    tokenPreference.setOnPreferenceClickListener {
        showSettingsInputDialog(
            title = getString(R.string.tmdb_token_title),
            message = getString(R.string.tmdb_token_help),
            initialValue = TmdbSettings.token(requireContext()),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            validate = { value ->
                if (TmdbSettings.validApiKeyInput(TmdbSettings.normalizeToken(value))) null
                else getString(R.string.tmdb_token_invalid)
            },
        ) { value ->
            val token = TmdbSettings.normalizeToken(value)
            val message = if (!TmdbSettings.validApiKeyInput(token)) {
                R.string.tmdb_token_invalid
            } else {
                try {
                    TmdbSettings.saveToken(requireContext(), token)
                    verification.verifyKey(token)
                    if (token.isEmpty()) R.string.tmdb_token_removed else R.string.tmdb_key_checking
                } catch (_: IOException) {
                    R.string.tmdb_token_save_failed
                }
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
        true
    }
}

private class TmdbPreferenceVerification(
    private val fragment: PreferenceFragmentCompat,
    private val preference: Preference,
) : DefaultLifecycleObserver {
    private val context = fragment.requireContext().applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, LinkedBlockingQueue())
    private var pending: Future<*>? = null
    private var generation = 0
    private var closed = false

    fun restoreOrVerify() {
        refreshSummary()
        val key = TmdbSettings.token(context)
        if (key.isNotEmpty() && TmdbSettings.verificationResult() == null) verifyKey(key)
    }

    private fun refreshSummary() {
        val key = TmdbSettings.token(context)
        preference.setSummary(
            if (key.isEmpty()) R.string.tmdb_token_missing
            else TmdbSettings.verificationResult()?.summaryRes() ?: R.string.tmdb_token_saved,
        )
    }

    fun verifyKey(key: String) {
        cancelPending()
        refreshSummary()
        if (key.isEmpty() || closed) return
        preference.setSummary(R.string.tmdb_key_checking)
        val requestGeneration = generation
        pending = worker.submit {
            val result = TmdbKeyVerifier().verify(key)
            handler.post {
                val currentRequest = !closed && generation == requestGeneration
                if (currentRequest && TmdbSettings.recordVerification(context, key, result)) {
                    pending = null
                    refreshSummary()
                    if (fragment.isResumed) Toast.makeText(context, result.summaryRes(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cancelPending() {
        generation++
        pending?.cancel(true)
        pending = null
        worker.purge()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        closed = true
        cancelPending()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }
}

private fun TmdbKeyVerification.summaryRes(): Int = when (this) {
    TmdbKeyVerification.VERIFIED -> R.string.tmdb_key_verified
    TmdbKeyVerification.REJECTED -> R.string.tmdb_key_rejected
    TmdbKeyVerification.UNAVAILABLE -> R.string.tmdb_key_unavailable
}
