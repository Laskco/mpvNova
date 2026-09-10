# Android Lint Audit

Audit date: September 10, 2026. Baseline: `565abba0`.

## Method

Run Android Lint 9.4.0 on a detached local checkout with all 17 globally disabled checks restored and source-level `tools:ignore`, `SuppressLint`, `Suppress`, `SuppressWarnings`, and `noinspection` suppressions removed. Inspect default release, API-29 release, and default debug separately. Keep API availability annotations and normal resource/build configuration intact. This is an unsuppressed run of the normally enabled lint checks, not an opt-in run of every experimental lint detector.

The default release produced **1,428 findings**: 1 fatal, 1,139 errors, and 288 warnings. API-29 release produced 1,424, and default debug produced 1,440. Variants share most findings; these totals must not be added together as unique bugs.

The largest groups were 1,127 missing-translation findings and 137 optional KTX substitutions. Error severity does not make an untranslated string a runtime crash: Android falls back to the default-language resource. The fixed-size RecyclerView fatal finding is a false positive for this specific match-parent file-picker list.

Raw reports and the suppression-removal script are local-only under `app/build/lint-audit`. They are intentionally not shipped or committed. The audit checkout uses the same tracked native libraries and does not rebuild or update dependencies.

## Policy

Only `UseKtx` remains globally disabled. Equivalent KTX rewrites are optional style changes, not a reason to churn otherwise working code. Accessibility, API, storage, dependency, text, and layout checks are enabled for future changes.

Local suppressions are allowed when their scope and reason are clear. They must not be used to claim that unsuppressed lint is clean. A suppression location is not the same as a suppressed finding count.

## Intentional Exceptions and Remaining Work

| Finding | Disposition |
| --- | --- |
| MissingTranslation | Translation backlog. Preserve existing translations and English fallback; do not invent translations or mark user-facing text non-translatable to hide findings. |
| UseKtx | Equivalent convenience APIs; the one global style exclusion. |
| RestrictedApi | The preference adapter hook uses AndroidX's restricted preference-to-row mapping for TV styling and focus. Keep the existing narrow suppression documented; replacing the adapter is a separate behavioral change. |
| StaticFieldLeak | The file loader keeps the overridable fragment comparison hook. It releases observers and old views, but a blocked filesystem call can retain the fragment until it returns. This remaining lifetime limitation is not claimed to be eliminated. The document loader and pending-export strong activity references were removed. |
| InvalidSetHasFixedSize | The actual file-picker RecyclerView is match-parent in both axes. Keep its statement-level suppression; unrelated wrap-content lists do not make this list content-sized. |
| NotificationPermission | Android media-session notifications are exempt. Tokenless updates need permission; foreground-service startup must not be blocked by this check. |
| PictureInPictureIssue | Auto-enter is intentionally not enabled: PiP remains user-controlled. Supply source bounds for transitions without enabling automatic PiP. |
| ScopedStorage | Explicit all-files access supports the path-based media browser and sidecar/config files; document picker access remains available. This is not a claim of Google Play policy eligibility. |
| OldTargetApi | Target 36 and the intentional target-29 compatibility flavor are unchanged. A target-SDK migration requires separate device testing. |
| ChromeOsAbiSupport | Lint cannot infer the dynamic ABI include loop. All four ABI directories, including x86_64, are present; inspect packaged APKs rather than changing the split policy to silence it. |
| DiscouragedApi | TV landscape orientation remains intentional. Do not change activity orientation in a lint cleanup. Debug diagnostics also use resource-name lookup intentionally. |
| SmallSp | Existing 10sp secondary labels remain a readability/design follow-up. Do not silently resize tuned TV panels. The check is now enabled. |
| UselessParent, NestedWeights, InefficientWeight, TooManyViews | Review runtime ownership and sizing before restructuring. Some parents are used for styling/scrolling, and the fallback panel deliberately starts at 300dp and shrinks. Preserve the tested panel geometry; the media-picker view count remains a performance follow-up. |
| ButtonStyle | Custom bordered TV buttons intentionally differ from Android's borderless dialog-button recommendation. |
| RelativeOverlap | Player overlays have user-configurable positioning; the static XML overlap warning does not justify overriding saved placement. |
| SetTextI18n | Numeric edit fields intentionally use ASCII values that their existing parsers accept. Localized digit formatting must not break saving those fields. |
| PluralsCandidate | Remaining suppressed cases are invariant unit abbreviations, count-independent status labels, and the fixed shader limit. Variable shader/file counts use plurals. |
| LabelFor | The generic settings dialog assigns the label text at runtime. The label/input association is explicit in XML; the label's static-text warning is narrowly suppressed. |

## Changes

- Make document-browser loading independent of fragment/view state. Deliver metadata caches with completed results instead of mutating shared caches from canceled workers.
- Destroy file-browser loaders and release adapters/views when the view is destroyed. Snapshot file-browser worker inputs and stop directory observers on stop, reset, and abandonment.
- Keep only a weak activity reference for pending legacy download permission requests; do not reopen export dialogs for a destroyed activity.
- Check notification permission for tokenless notification updates while preserving media-session exemptions and foreground-service startup.
- Remove unused Android 13 media permissions. Existing all-files access and document URI grants remain unchanged.
- Supply PiP source bounds without enabling automatic PiP. Stop declaring the rectangular launcher icon as a round icon; the artwork itself is unchanged.
- Add accessibility labels and input associations, preserve selected indicators, and exclude decorative icons from accessibility.
- Move diagnostic text to debug-only resources, format runtime text through resources, pluralize shader counts, correct the English playlist count wording, and use typographic ellipses.
- Remove obsolete suppressions and enable 16 previously disabled lint checks. Preserve existing layout dimensions, weights, focus navigation, decoder behavior, and dependencies.

## Final Results

| Variant | Configured errors | Configured warnings | Fully unsuppressed findings |
| --- | ---: | ---: | ---: |
| Default release | 0 | 35 | 1,363 |
| API-29 release | 0 | 35 | 1,361 |
| Default debug | 0 | 37 | 1,367 |

Default-release unsuppressed severity totals are 1 fatal, 1,145 errors, and 217 warnings. Of those findings, 1,133 are missing translations and 137 are optional KTX substitutions. New accessibility strings and split shader plural resources add six translation findings; these remain visible in the unsuppressed audit and use the established English fallback.

The 35 configured release warnings are 27 small-text labels, five fixed-orientation warnings, one dynamic-ABI detection warning, one target-SDK warning, and one media-picker view-count warning. Debug adds two resource-lookup warnings. None is silently re-disabled to produce a zero-warning report.

Global disabled checks decreased from 17 to 1. Local lint suppression sites decreased from 71 to 60: 54 XML attributes, five `SuppressLint` annotations, and one `noinspection` comment. This count excludes non-lint Kotlin/Java compiler and Detekt suppressions, and does not represent a count of bugs.

## Verification

- Default release, API-29 release, and default debug lint tasks passed. The separate unsuppressed audit also completed for all three variants.
- All eight existing default-debug unit tests passed; Detekt reported no findings.
- Signed default-release APKs built successfully. ARM64 signature verification passed with APK signature scheme v2; the universal APK contains all four native ABIs, including x86_64.
- XML parsing/resource checks passed. Existing geometry and focus attributes were unchanged across all 24 modified layouts; added complementary zero padding preserves the intended asymmetric insets.
- The signed x86 release ran on an isolated API-28 TV emulator. Local folder navigation, back/reopen, file-observer refresh, activity recreation, settings navigation, and the runtime-labeled audio-language dialog were exercised. No mpvNova crash was recorded. The activity's existing recreation behavior returns the file browser to its initial destination.
- Full document-provider, TalkBack, API-33 notification-permission, and PiP transition device tests remain outstanding. This TV emulator has no `OPEN_DOCUMENT_TREE` activity. Those paths were reviewed statically; limited emulator checks do not replace device coverage.

No dependencies or native libraries were changed.

## References

- [Android lint configuration and scoped suppression](https://developer.android.com/studio/write/lint.html)
- [Notification permission exemptions for media sessions](https://developer.android.com/develop/ui/views/notifications/notification-permission#media-sessions)
- [Picture-in-picture transitions](https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition)

Device smoke tests and lint cannot prove all Android versions, document providers, remote-control models, translations, or saved custom layouts are regression-free.
