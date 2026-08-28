package app.mpvnova.player

import android.app.Application

/**
 * Process-wide setup. Two things wired here so they're alive before any
 * activity runs and stay alive across the whole process lifetime:
 *
 *   - [MpvLogRingBuffer]: collects mpv log lines for the support bundle
 *     and crash reports.
 *   - [CrashReporter]: catches uncaught exceptions on any thread and
 *     writes a one-shot report file the user can ship to us via the
 *     support-bundle export.
 *
 * Nothing here touches the network or runs anything heavy on the main
 * thread — it's a few field assignments plus registering a couple of
 * observers / handlers.
 */
internal class MpvNovaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        MpvLogRingBuffer.install(this)
    }
}
