package app.mpvnova.player.preferences

import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import app.mpvnova.player.R
import app.mpvnova.player.readSegmentSkipModes

class SegmentSkippingPreference : PreferenceActivity.StyledPreferenceFragment(R.xml.pref_segment_skipping) {
    override fun onPreferencesLoaded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        // Seed each new episode choice from the old combined preference, not XML defaults.
        readSegmentSkipModes(prefs.all).forEach { (kind, mode) ->
            findPreference<ListPreference>(kind.preferenceKey)?.apply {
                value = mode.prefValue
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            }
        }
        findPreference<ListPreference>("skip_button_display")?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
    }
}
