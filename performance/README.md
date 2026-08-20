# mpvNova performance harness

The module measures release-like builds of mpvNova with Macrobenchmark and generates the app's Baseline Profile.

## Device contract

Create and push the synthetic test pattern with:

`./performance/scripts/prepare_benchmark_media.ps1 -Ffmpeg C:/path/to/ffmpeg.exe`

The script generates a 60 second 1280x720 H.264 test pattern with synthetic audio. It does not use personal media. The resulting file is placed at:

`/sdcard/Download/mpvnova-benchmark.mp4`

Override that location with:

`-Pandroid.testInstrumentationRunnerArguments.mediaUri=file:///sdcard/Download/other.mp4`

The local low-end reference profile uses Android API 28, 1.5 GB RAM, two CPU cores, 1920x1080 output, and SwiftShader software rendering. API 28 is the oldest system image currently installed on the development machine; the app itself supports API 23.

## Commands

Run the instrumentation smoke test:

`./gradlew :app:connectedDefaultDebugAndroidTest`

Run startup, playback-start frame timing, and playback memory benchmarks:

`./gradlew :performance:connectedDefaultBenchmarkBenchmarkAndroidTest`

Measure mpv's native dropped-frame counters across ten unpause transitions:

`./performance/scripts/measure_playback_transition.ps1`

Generate and copy the Baseline Profile into the app:

`./gradlew :app:generateDefaultReleaseBaselineProfile`

Benchmark output, JSON results, and Perfetto traces are written below `performance/build/outputs`.
