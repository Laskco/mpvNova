package app.mpvnova.performance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue

internal const val TARGET_PACKAGE = "app.mpvnova.player"
private const val BENCHMARK_PACKAGE = "app.mpvnova.performance"
private const val DEFAULT_MEDIA_URI = "file:///sdcard/Download/mpvnova-benchmark.mp4"

internal fun benchmarkArguments(): Bundle =
    InstrumentationRegistry.getArguments()

internal fun benchmarkMediaUri(): String =
    benchmarkArguments().getString("mediaUri", DEFAULT_MEDIA_URI)

internal fun MacrobenchmarkScope.assertBenchmarkMediaExists() {
    val uri = Uri.parse(benchmarkMediaUri())
    if (uri.scheme != "file") return

    device.executeShellCommand(
        "pm grant $TARGET_PACKAGE android.permission.READ_EXTERNAL_STORAGE",
    )
    device.executeShellCommand(
        "pm grant $BENCHMARK_PACKAGE android.permission.READ_EXTERNAL_STORAGE",
    )
    assertTrue(
        "Push a deterministic sample to ${uri.path} or pass -Pandroid.testInstrumentationRunnerArguments.mediaUri=...",
        uri.path?.let(::File)?.canRead() == true,
    )
}

internal fun MacrobenchmarkScope.startPlaybackAndWait() {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.MPVActivity")
        setDataAndType(Uri.parse(benchmarkMediaUri()), "video/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivityAndWait(intent)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.pausePlayback() {
    device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
}

internal fun MacrobenchmarkScope.resumePlayback() {
    device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
}
