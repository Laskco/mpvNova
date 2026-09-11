package app.mpvnova.player

import android.app.Application
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

/**
 * Process-wide setup. Diagnostics are wired here so they're alive before any
 * activity runs and stay alive across the whole process lifetime:
 *
 *   - [MpvLogRingBuffer]: collects mpv log lines for the support bundle
 *     and crash reports.
 *   - [CrashReporter]: catches uncaught exceptions on any thread and
 *     writes a one-shot report file the user can ship to us via the
 *     support-bundle export.
 *
 * Preference migrations run before activities can read settings. Disk writes
 * are asynchronous; nothing here touches the network.
 */
internal class MpvNovaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        MpvLogRingBuffer.install(this)
        getDefaultSharedPreferences(this).migratePreferredSubtitleOptIn()
    }
}
