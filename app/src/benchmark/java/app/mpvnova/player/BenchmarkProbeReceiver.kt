package app.mpvnova.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Exposes native playback counters only in the non-debuggable benchmark build. */
class BenchmarkProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUERY_PLAYBACK) return

        resultCode = Activity.RESULT_OK
        resultData = listOf(
            "pause=${mpvGetPropertyBoolean("pause")}",
            "timePos=${mpvGetPropertyDouble("time-pos")}",
            "decoderDrops=${mpvGetPropertyInt("decoder-frame-drop-count")}",
            "voDrops=${mpvGetPropertyInt("frame-drop-count")}",
            "mistimed=${mpvGetPropertyInt("mistimed-frame-count")}",
            "delayed=${mpvGetPropertyInt("vo-delayed-frame-count")}",
        ).joinToString(separator = ",")
    }

    private companion object {
        const val ACTION_QUERY_PLAYBACK = "app.mpvnova.player.BENCHMARK_QUERY_PLAYBACK"
    }
}
