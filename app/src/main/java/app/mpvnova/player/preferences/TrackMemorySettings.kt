package app.mpvnova.player.preferences

import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import app.mpvnova.player.R
import app.mpvnova.player.RememberedTrackKind
import app.mpvnova.player.clearTrackMemory
import app.mpvnova.player.clearWatchLaterTracks
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PreferenceFragmentCompat.bindTrackMemorySettings() {
    bindTrackMemoryReset(
        "clear_remembered_audio", RememberedTrackKind.AUDIO,
        R.string.pref_clear_remembered_audio_message, R.string.remembered_audio_cleared,
    )
    bindTrackMemoryReset(
        "clear_remembered_subtitles", RememberedTrackKind.SUBTITLE,
        R.string.pref_clear_remembered_subtitles_message, R.string.remembered_subtitles_cleared,
    )
}

private fun PreferenceFragmentCompat.bindTrackMemoryReset(
    key: String,
    kind: RememberedTrackKind,
    @StringRes messageRes: Int,
    @StringRes successRes: Int,
) {
    findPreference<Preference>(key)?.setOnPreferenceClickListener { preference ->
        showSettingsConfirmationDialog(
            title = preference.title ?: "",
            message = getString(messageRes),
            confirmText = getString(R.string.dialog_clear),
        ) {
            clearAllTrackChoices(kind, successRes)
        }
        true
    }
}

private fun PreferenceFragmentCompat.clearAllTrackChoices(kind: RememberedTrackKind, @StringRes successRes: Int) {
    val host = requireActivity()
    val context = host.applicationContext
    val progress = host.showSettingsProgressDialog(getString(R.string.clearing_remembered_tracks))
    lifecycleScope.launch {
        val complete = try {
            clearTrackMemory(getDefaultSharedPreferences(context), kind)
            withContext(Dispatchers.IO) { clearWatchLaterTracks(context.filesDir, kind) }
        } catch (error: IOException) {
            Log.w("mpvnova-track-memory", "Couldn't clear saved resume choices", error)
            false
        } finally {
            progress.dismiss()
        }
        Toast.makeText(
            context, if (complete) successRes else R.string.clear_remembered_tracks_incomplete, Toast.LENGTH_SHORT,
        ).show()
    }
}
