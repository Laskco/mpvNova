package app.mpvnova.player

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import androidx.preference.PreferenceManager

internal const val NETWORK_DEFAULT = "default"
private const val NETWORK_MIB = 1024L * 1024L
@Volatile private var cachedNetworkBudgetMiB: Int? = null

internal enum class NetworkUnit { MIB, KIB, SECONDS, TOGGLE, PRESET }

@Suppress("MagicNumber") // User-facing option ranges and quick choices.
internal enum class NetworkSetting(
    val option: String,
    val titleRes: Int,
    val summaryRes: Int,
    val unit: NetworkUnit,
    val minimum: Int = 0,
    val maximum: Int = 0,
    val choices: List<Int> = emptyList(),
    val nextStream: Boolean = false,
) {
    PRESET("", R.string.network_preset, R.string.network_preset_summary, NetworkUnit.PRESET),
    FORWARD("demuxer-max-bytes", R.string.network_forward, R.string.network_forward_summary,
        NetworkUnit.MIB, 8, 512, listOf(32, 64, 96, 128, 256, 512)),
    BACKWARD("demuxer-max-back-bytes", R.string.network_backward, R.string.network_backward_summary,
        NetworkUnit.MIB, 0, 128, listOf(0, 8, 16, 32, 64, 128)),
    TARGET("cache-secs", R.string.network_target, R.string.network_target_summary,
        NetworkUnit.SECONDS, 1, 600, listOf(15, 30, 60, 120, 180, 300, 600)),
    PAUSE("cache-pause", R.string.network_pause, R.string.network_pause_summary, NetworkUnit.TOGGLE),
    WAIT("cache-pause-wait", R.string.network_wait, R.string.network_wait_summary,
        NetworkUnit.SECONDS, 1, 30, listOf(1, 2, 3, 5, 10, 15, 30)),
    READAHEAD("demuxer-readahead-secs", R.string.network_readahead, R.string.network_readahead_summary,
        NetworkUnit.SECONDS, 0, 600, listOf(0, 1, 5, 15, 30, 60, 120)),
    STREAM("stream-buffer-size", R.string.network_stream, R.string.network_stream_summary,
        NetworkUnit.KIB, 4, 4096, listOf(64, 128, 256, 512, 1024, 4096), nextStream = true),
    TIMEOUT("network-timeout", R.string.network_timeout, R.string.network_timeout_summary,
        NetworkUnit.SECONDS, 5, 300, listOf(10, 20, 30, 60, 120, 300), nextStream = true);

    val key: String get() = "network_${name.lowercase(java.util.Locale.ROOT)}"

    fun mpvValue(value: String): String = when (unit) {
        NetworkUnit.MIB -> (value.toLong() * NETWORK_MIB).toString()
        NetworkUnit.KIB -> (value.toLong() * 1024L).toString()
        else -> value
    }

    fun valid(value: String): Boolean = when (unit) {
        NetworkUnit.TOGGLE -> value == "yes" || value == "no"
        NetworkUnit.PRESET -> false
        else -> value.toIntOrNull()?.let { it in minimum..maximum } == true
    }
}

@Suppress("MagicNumber") // Deliberate preset values, in MiB and seconds.
internal enum class NetworkPreset(val titleRes: Int, val summaryRes: Int, val values: Map<NetworkSetting, String>) {
    DEFAULT(R.string.network_default, R.string.network_default_summary, emptyMap()),
    LIGHT(R.string.network_light, R.string.network_light_summary, networkPresetValues(32, 8, 30, 2)),
    BALANCED(R.string.network_balanced, R.string.network_balanced_summary, networkPresetValues(64, 16, 120, 3)),
    UNSTEADY(R.string.network_unsteady, R.string.network_unsteady_summary, networkPresetValues(96, 16, 180, 5)),
    LARGE(R.string.network_large, R.string.network_large_summary, networkPresetValues(256, 32, 180, 3)),
}

private fun networkPresetValues(forward: Int, back: Int, target: Int, wait: Int) = mapOf(
    NetworkSetting.FORWARD to forward.toString(),
    NetworkSetting.BACKWARD to back.toString(),
    NetworkSetting.TARGET to target.toString(),
    NetworkSetting.PAUSE to "yes",
    NetworkSetting.WAIT to wait.toString(),
)

@Suppress("MagicNumber") // Bound custom cache use without changing the legacy defaults.
internal fun networkMemoryBudgetMiB(context: Context): Int {
    cachedNetworkBudgetMiB?.let { return it }
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager?.getMemoryInfo(memory)
    val platformLimit = if (!Process.is64Bit() || manager?.isLowRamDevice == true) 128 else 512
    val ramLimit = if (memory.totalMem > 0) (memory.totalMem / NETWORK_MIB / 4).toInt() else 128
    return minOf(platformLimit, ramLimit).coerceAtLeast(128).also { cachedNetworkBudgetMiB = it }
}

internal fun readNetworkOverrides(prefs: SharedPreferences, budgetMiB: Int): Map<NetworkSetting, String> {
    val stored = prefs.all
    val values = NetworkSetting.entries.mapNotNull { setting ->
        val raw = stored[setting.key] as? String
        raw?.takeIf(setting::valid)?.let { setting to it }
    }.toMap().toMutableMap()
    // Backward cache shares the memory allowance. Revalidate backups imported from larger devices.
    val defaultMiB = defaultDemuxerCacheBytes() / NETWORK_MIB.toInt()
    values[NetworkSetting.BACKWARD]?.toInt()?.let {
        val reserved = if (NetworkSetting.FORWARD in values) NetworkSetting.FORWARD.minimum else defaultMiB
        values[NetworkSetting.BACKWARD] = minOf(it, budgetMiB - reserved).toString()
    }
    val back = values[NetworkSetting.BACKWARD]?.toInt() ?: defaultMiB
    val forwardLimit = (budgetMiB - back).coerceAtLeast(NetworkSetting.FORWARD.minimum)
    values[NetworkSetting.FORWARD]?.toInt()?.let {
        values[NetworkSetting.FORWARD] = minOf(it, forwardLimit).toString()
    }
    return values
}

internal fun Context.networkOverrides(): Map<NetworkSetting, String> = readNetworkOverrides(
    PreferenceManager.getDefaultSharedPreferences(this), networkMemoryBudgetMiB(this),
)

internal fun networkValueFits(values: Map<NetworkSetting, String>, budgetMiB: Int): Boolean {
    val defaultMiB = defaultDemuxerCacheBytes() / NETWORK_MIB.toInt()
    val forward = values[NetworkSetting.FORWARD]?.toInt() ?: defaultMiB
    val back = values[NetworkSetting.BACKWARD]?.toInt() ?: defaultMiB
    return forward + back <= budgetMiB
}

internal fun writeNetworkPreset(prefs: SharedPreferences, preset: NetworkPreset) {
    val editor = prefs.edit()
    NetworkSetting.entries.forEach { editor.remove(it.key) }
    preset.values.forEach { (setting, value) -> editor.putString(setting.key, value) }
    editor.apply()
}

/** Holds the user's mpv.conf defaults so resetting a live override restores them, not guessed values. */
internal class NetworkOptionSession {
    private val original = mutableMapOf<NetworkSetting, String>()
    private var applied = emptyMap<NetworkSetting, String>()

    @Synchronized
    fun initialize(read: (String) -> String?) {
        original.clear()
        applied = emptyMap()
        NetworkSetting.entries.filter { it.option.isNotEmpty() }.forEach { setting ->
            read(setting.option)?.takeIf(String::isNotBlank)?.let { original[setting] = it }
        }
    }

    fun apply(values: Map<NetworkSetting, String>, write: (String, String) -> Unit) {
        val changed = (applied.keys + values.keys).filter { applied[it] != values[it] }
        // Release memory before increasing another cap during a preset switch.
        val ordered = changed.sortedBy { setting ->
            val next = values[setting]?.let(setting::mpvValue) ?: original[setting]
            val previous = applied[setting]?.let(setting::mpvValue) ?: original[setting]
            if ((next?.toLongOrNull() ?: Long.MAX_VALUE) < (previous?.toLongOrNull() ?: 0L)) 0 else 1
        }
        ordered.forEach { setting ->
            val value = values[setting]?.let(setting::mpvValue) ?: original[setting]
            if (value != null) write(setting.option, value)
        }
        applied = values.toMap()
    }

    fun verify(read: (String) -> String?): List<String> = NetworkSetting.entries
        .filter { it.option.isNotEmpty() }
        .map { setting ->
            val expected = applied[setting]?.let(setting::mpvValue) ?: original[setting]
            val actual = read(setting.option)?.takeIf(String::isNotBlank)
            val matches = expected != null && actual != null &&
                (expected == actual || (expected.toDoubleOrNull()?.let { it == actual.toDoubleOrNull() } == true))
            val status = when {
                expected == null || actual == null -> "UNAVAILABLE"
                matches -> "MATCH"
                else -> "MISMATCH"
            }
            val timing = if (setting.nextStream) "next-open option, not current stream" else "live option"
            "${setting.option}: expected=$expected actual=$actual status=$status ($timing)"
        }
}

// Read preferences under the same lock as writes so a waiting decoder callback
// cannot apply a stale snapshot after a newer drawer selection.
internal fun MPVView.applyNetworkSettings(verify: Boolean = false) = synchronized(networkOptionSession) {
    if (!MpvRuntimeOwnership.isOwnedBy(this)) return@synchronized
    val values = context.networkOverrides()
    networkOptionSession.apply(values, ::mpvSetPropertyString)
    if (verify) {
        val preset = NetworkPreset.entries.firstOrNull { it.values == values }?.name ?: "CUSTOM"
        Log.i("mpv-network", "Read-back for preset=$preset")
        networkOptionSession.verify(::getOptionString).forEach { Log.i("mpv-network", it) }
    }
}
