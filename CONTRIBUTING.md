# Contributing to mpvNova

Keep pull requests focused on one problem. Read the [settings guide](docs/settings-guide.md) and search existing issues before opening a report or starting work.

## What is accepted

- Reproducible bug fixes with a linked issue.
- Small maintenance changes with a clear reason.
- Documentation corrections and translations.
- Features and larger changes that have explicit maintainer approval first.

New features, UI redesigns, behavior changes unrelated to a bug, dependency additions, and broad refactors need an approved issue before implementation. Link the maintainer's approval comment in the PR. An open issue, an enhancement label, or passing checks is not approval.

Cosmetic-only changes and unrelated cleanup bundled into a fix will not be reviewed. Incomplete or out-of-scope PRs may be closed without review.

## Bug fixes

Link a report that explains the affected version, device, Android version, reproduction steps, and expected versus actual behavior. Include logs for crashes and playback failures, with private URLs and credentials removed.

Fix the cause with the smallest reasonable change. UI changes need before/after screenshots or a video, including remote navigation when relevant. Playback changes need testing with the affected playback path, not just a successful build.

General mpv or mpv-android features belong upstream. If an issue is marked `waiting-for-upstream`, discuss an alternative with the maintainer before submitting a separate implementation.

## Before submitting

Complete the PR template and describe what you actually tested. Include device or emulator details for manual testing and the commands used for automated checks. Explain any checks you could not run.

For app changes, run:

```sh
./gradlew :app:lintDefaultDebug :app:testDefaultDebugUnitTest :app:detekt
./gradlew :app:assemble
```

On Windows, use `./gradlew.bat`. See the [build instructions](README.md#building) for prerequisites. Native-library changes also need reproducible build details and playback testing; do not submit unexplained binary replacements.

## PR checks and test APKs

PRs run template validation, Android lint, unit tests, Detekt, CodeQL, and APK builds. The template check validates the description; it does not verify test claims or grant maintainer approval.

The build posts a link to `pr-debug-apks`, containing universal debug APKs for the normal and target-API-29 variants. Downloads require signing in to GitHub and expire after 14 days. These are unreviewed test builds, not official releases. Their debug signatures cannot update an installed official release; use a test device or emulator.

Release builds also run to check R8 optimization. Release signing keys are not provided to PR builds.
