# mpvNova

mpvNova is an Android TV-first fork of [mpv-android](https://github.com/mpv-android/mpv-android), built on [libmpv](https://github.com/mpv-player/mpv).

It keeps mpv's playback power, but reshapes the app around a cleaner TV experience: leanback launcher support, remote-friendly navigation, a TV home screen, darker full-screen UI, and faster access to the playback controls that matter most on a couch setup.

## Screenshots

<table>
  <tr>
    <td align="center"><strong>Home screen</strong><br><img src="docs/screenshots/home-screen.png" alt="mpvNova home screen" width="100%"></td>
    <td align="center"><strong>Media library</strong><br><img src="docs/screenshots/media-library.png" alt="Media library browser" width="100%"></td>
    <td align="center"><strong>Settings overview</strong><br><img src="docs/screenshots/settings-overview.png" alt="Settings overview" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><strong>Decoder settings</strong><br><img src="docs/screenshots/settings-decoder-mode-dialog.png" alt="Preferred decoder mode settings dialog" width="100%"></td>
    <td align="center"><strong>Player controls</strong><br><img src="docs/screenshots/player-controls.png" alt="Player controls overlay" width="100%"></td>
    <td align="center"><strong>Decoder picker</strong><br><img src="docs/screenshots/player-decoder-mode.png" alt="Player decoder mode picker" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><strong>Subtitle panel</strong><br><img src="docs/screenshots/player-subtitles.png" alt="Subtitle selection panel" width="100%"></td>
    <td align="center"><strong>Audio panel</strong><br><img src="docs/screenshots/player-audio.png" alt="Audio selection and filter panel" width="100%"></td>
    <td align="center"><strong>Stats overlay</strong><br><img src="docs/screenshots/player-stats-overlay.png" alt="Playback stats overlay" width="100%"></td>
  </tr>
</table>

## Highlights

- Android TV / Google TV launcher support with leanback entry points and TV banner assets
- TV-first home screen with quick actions for folders, storage, URL playback, and settings
- Redesigned player HUD with stronger D-pad focus behavior, chapter markers, and a custom TV seek bar
- In-player decoder picker, including the dedicated `NVIDIA Shield Pro 2019 / Hi10P x264 anime` mode
- Custom TV subtitle and audio panels
- Built-in TV audio controls for voice boost, volume boost, night mode, audio normalization, dialogue downmix, surround-state feedback, and filter persistence

## What It Adds

- A TV-first shell with leanback launcher support, banner assets, and a custom home screen
- A redesigned playback UI built around TV navigation instead of touch-first controls
- Custom subtitle, audio, and decoder pickers designed for couch use
- Dialogue-focused audio controls for surround playback tuning on Android TV

For the broader inherited playback feature set, config support, and core mpv-android capabilities, see the upstream [mpv-android](https://github.com/mpv-android/mpv-android) project.

## Installation

- Build an APK from this repo
- Sideload it on Android TV / Google TV
- Use the universal APK if you want one build that works across device architectures

## Build Notes

### App-only build

Use this when the bundled native libraries are already present and you mainly want to build or test the Android app layer.

**Windows**

```powershell
cmd /c gradlew.bat :app:assembleDefaultDebug
```

**Linux / macOS**

```bash
./gradlew :app:assembleDefaultDebug
```

### Release signing

Release signing is optional for local debug builds, but required for signed release APKs.

- Keep your real signing files in `keystore.properties` and `keystore/`
- Those paths are intentionally ignored by Git
- Start from [keystore.properties.example](keystore.properties.example) for the local file shape
- In CI or other non-local environments, use `MPVNOVA_STORE_FILE`, `MPVNOVA_STORE_PASSWORD`, `MPVNOVA_KEY_ALIAS`, and `MPVNOVA_KEY_PASSWORD`

### Full native rebuild

Use this when you need to rebuild `libmpv`, ffmpeg, or the JNI/native layer.

The native rebuild flow lives in [buildscripts/README.md](buildscripts/README.md) and is supported on Linux and macOS. It is not intended to run natively on Windows.

## APK Variants

The Gradle config currently builds:

- `universal`: all bundled ABIs in one APK
- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

There is also an `api29` flavor for older-target compatibility builds.

## Publishing To GitHub Safely

Before pushing this fork to a public GitHub repository:

- Confirm `keystore.properties`, `keystore/`, and `local.properties` are still ignored
- Never commit a release keystore, signing passwords, or machine-specific SDK paths
- Review exactly what will be published with `git diff --cached --stat` before the first push
- Prefer creating the GitHub repo as private first if you want one last review pass before going public
- Keep screenshots, README copy, and release notes aligned with the current TV UI before making the repo public
- If you later publish release APKs, upload built artifacts to GitHub Releases rather than committing them into the repo

## Acknowledgments

- [mpv-android](https://github.com/mpv-android/mpv-android)
- [mpv](https://github.com/mpv-player/mpv)
- everyone whose work made the upstream Android port and playback stack possible
