# Offline anime filename parser

mpvNova uses **anitomy-ng 1.0.9**, pinned exactly in `Cargo.toml` with transitive
dependencies locked in `Cargo.lock`. `rust-toolchain.toml` pins Rust. The build
uses Android NDK **29.0.14206865**, API 23, and 16 KiB-compatible ELF alignment.

The JNI bridge returns alternating element-kind/value strings. Duplicate fields
are preserved so the Kotlin resolver can reject ambiguous episode ranges. The
parser is separate from mpv, FFmpeg, Lua, curl, and Dolby Vision processing.
No API key or network request is needed at playback time.

## Build locally

Install Rust through rustup and the pinned Android NDK. From the repository root,
using PowerShell 7 on Windows, Linux, or macOS:

```powershell
./buildscripts/build-anitomy.ps1 -AndroidNdk /path/to/ndk/29.0.14206865
```

This builds all four architectures and copies only `libmpvnova_anitomy.so` into
their existing `app/src/main/jniLibs` directories. Gradle packages these prebuilts;
ordinary APK builds do not require Rust. The `build-anitomy` GitHub workflow can
also produce downloadable parser-only artifacts without rebuilding the player.

For local JVM/JNI regression tests, build the host library separately:

```powershell
./buildscripts/build-anitomy.ps1 -HostOnly
```

Set the test JVM's `java.library.path` to `native/anitomy/target/release`. The host
DLL/shared library is for local tests only; never put it in the Android APK.

## Updating anitomy-ng

1. Read the upstream release notes and change the exact `anitomy-ng` version in
   `Cargo.toml`. Do not switch to an unpinned branch or wildcard version.
2. In this directory, run `cargo update -p anitomy-ng --precise NEW_VERSION` and
   review the resulting `Cargo.lock` changes. Update `rust-toolchain.toml` only
   when needed and update the version/source link in `docs/licenses.html`.
3. Rebuild the host bridge and run the filename/metadata regression cases. Include
   launcher season/episode priority, real brackets and years, release credits,
   Unicode titles, multiple episodes, and missing or misleading metadata.
4. Rebuild all four Android parser libraries locally, or push the reviewed source
   to an approved branch and run the `build-anitomy` workflow on that branch.
   Download the parser artifact and replace only its four parser libraries.
5. Build and verify the APKs, including ARM32 and the API-29 flavor. Review the
   source/lockfile/binary diff together before committing and releasing.

Parser updates ship as app updates. The app does not download executable parser
updates or silently change parsing rules. Nuvio-supplied season and episode data
retain priority; an offline filename parser cannot establish a missing series ID.

Upstream: https://github.com/tylergibbs2/anitomy-ng (MPL-2.0).
The upstream parser is unmodified; this directory contains mpvNova's JNI bridge.
