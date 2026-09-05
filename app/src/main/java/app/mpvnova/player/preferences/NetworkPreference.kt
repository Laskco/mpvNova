package app.mpvnova.player.preferences

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import app.mpvnova.player.NetworkSetting
import app.mpvnova.player.R
import app.mpvnova.player.networkValueLabel
import app.mpvnova.player.showNetworkSetting

class NetworkPreference : PreferenceActivity.StyledPreferenceFragment(R.xml.pref_network) {
    override fun onPreferencesLoaded() {
        val buffering = requireNotNull(findPreference<PreferenceCategory>("network_buffering"))
        val stalls = requireNotNull(findPreference<PreferenceCategory>("network_stalls"))
        val advanced = requireNotNull(findPreference<PreferenceCategory>("network_advanced"))
        NetworkSetting.entries.forEach { setting ->
            val preference = Preference(requireContext()).apply {
                key = setting.key
                setTitle(setting.titleRes)
                isIconSpaceReserved = false
                isPersistent = false
                setOnPreferenceClickListener {
                    requireActivity().showNetworkSetting(setting, ::refreshValues)
                    true
                }
            }
            val group = when (setting) {
                NetworkSetting.PAUSE, NetworkSetting.WAIT -> stalls
                NetworkSetting.READAHEAD, NetworkSetting.STREAM, NetworkSetting.TIMEOUT -> advanced
                else -> buffering
            }
            group.addPreference(preference)
        }
        refreshValues()
    }

    override fun onResume() {
        super.onResume()
        refreshValues()
    }

    private fun refreshValues() {
        NetworkSetting.entries.forEach { setting ->
            val value = requireContext().networkValueLabel(setting)
            findPreference<Preference>(setting.key)?.summary = if (setting.nextStream) {
                getString(R.string.network_next_stream_value, value)
            } else {
                value
            }
        }
    }
}
