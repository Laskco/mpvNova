# mpvNova Settings Guide

This guide covers the settings and custom panels added by mpvNova. Some controls expose existing mpv features through a TV-friendly interface; those are included so you can understand the complete panel. It is not a reference for every mpv.conf option.

This describes the source on main, which may be ahead of the latest published APK. Defaults below are for a new installation. Saved preferences and imported presets can differ.

## Contents

- [Where to find settings](#where-to-find-settings)
- [Appearance](#appearance)
- [Player controls and remote navigation](#player-controls-and-remote-navigation)
- [Player bar customization](#player-bar-customization)
- [Clock and title customization](#clock-and-title-customization)
- [Title lookup](#title-lookup)
- [Subtitles](#subtitles)
- [Subtitle customization](#subtitle-customization)
- [Audio tools](#audio-tools)
- [Network and buffering](#network-and-buffering)
- [Decoders and device compatibility](#decoders-and-device-compatibility)
- [Video adjustments and shaders](#video-adjustments-and-shaders)
- [Skipping, seeking, and resume](#skipping-seeking-and-resume)
- [Screensaver](#screensaver)
- [Updates, backups, and support](#updates-backups-and-support)

## Where To Find Settings

Open Settings from the home screen for the full settings pages. During playback, the Settings button opens the player drawer. The Audio and Subtitles buttons open their own panels. Player UI customization and Clock & title customization have separate live editors in the player interface settings.

The settings drawer shares stored preferences with the main settings pages; it is not a separate profile. Live appearance changes are visible during editing. Decoder changes can interrupt playback while the pipeline is reconfigured. Network controls marked for the next stream do not change an already-open connection.

The player bar and clock/title editors provide **Editor actions** for undo, redo, reverting to the state when the editor opened, and preset import/export. Saving a named preset gives you a reusable setup; copying a surface copies appearance, not the other panel's position or layout.

## Appearance

Open **Settings > Appearance**, or the appearance controls in the player drawer.

| Setting | What it changes |
| --- | --- |
| Color Theme | The accent used across the app. Choose White, Crimson, Ocean, Cyan, Violet, Emerald, Lime, Amber, Gold, Copper, Indigo, Rose, Slate, Chrome, Oyster, or Ivory. |
| Interface font | The typeface used by the app interface, independently of subtitle fonts and title/clock styling. |
| Material You | Uses system dynamic colors where supported. |
| AMOLED Mode | Makes app backgrounds pure black. |
| Pure Black Surfaces | Extends pure black to cards, panels, and containers. |
| UI scale | Overall interface sizing. Default is 100%. Large values, especially 125% or above, can make panels too large for the screen. |

## Player Controls And Remote Navigation

Open **Settings > Player UI**, or the interface controls in the player drawer.

| Setting | What it changes |
| --- | --- |
| Auto-hide player controls | How long the controls remain visible after interaction. |
| Keep controls visible while paused | Keeps controls visible when paused until you hide them or resume. Default off. |
| Pause when controls show | Pauses while the controls overlay is open. Default off. This is separate from the Hi10P-only option below. |
| Place Settings and PiP in player bar | Moves the top-right actions into the bar. This placement setting defaults off. When moved into the bar, PiP is hidden by default; enable it under Player UI customization > Controls to show it. |
| D-pad up jumps to top controls | Up from the seekbar reaches the top-right actions. Default off; unavailable when those actions are in the bar. |
| Bottom controls | Places the player controls at the bottom. Available in the player drawer. |
| Back hides player UI first | The first Back press hides visible controls instead of starting the exit sequence. Default off. |
| Exit with double back press | Requires two Back presses within two seconds to exit. Default on. |
| Hide controls while seeking | Left/Right seeks without opening the normal controls. Default off. |
| Minimal seekbar while seeking | Shows a slim seekbar/time display instead of the full controls. Default off. This and Hide controls while seeking are alternative modes. |
| Remote button for next chapter | Assigns a remote key to the next-chapter action. Unassigned by default. |
| Reset player UI settings | Resets the UI preference group without resetting playback, audio, or subtitle settings. |

The chapter button skips on a short press; holding it with the remote opens the chapter picker. With the seekbar below the control row, Down from the row reaches it; with the seekbar above, Up reaches it.

## Player Bar Customization

Open **Player UI customization** during playback. The tabs are **Presets**, **Surface**, **Layout**, and **Controls**. These settings change the bar, not the video's resolution or subtitle appearance.

### Presets And Surface

| Setting | What it changes |
| --- | --- |
| Built-in presets | Default, Minimal, Cinema, Compact, Floating, and Edge to edge provide starting layouts. |
| Saved presets | Save and reuse your customized bar. The editor shows when you have modified a saved preset. |
| Surface style | Glass, Flat, or Transparent panel treatment. |
| Panel opacity | How opaque the bar background is. |
| Backdrop strength | Darkening behind the controls, separate from panel opacity. |
| Gradient | The panel's gradient treatment. |
| Panel outline / outline width | Whether the panel border is drawn and its thickness. |
| Corner radius | How rounded the panel corners are. |
| Elevation | The panel's raised/shadow appearance. |
| Button treatment | Minimal, Soft, or Block backgrounds for controls. |
| Icon/text outline | Adds contrast around control icons and labels. |

### Layout, Seekbar, And Time

| Setting | What it changes |
| --- | --- |
| Panel width | How much of the available horizontal space the bar occupies. |
| Density | Compact, Standard, or Comfortable spacing. |
| Vertical offset | Moves the bar vertically. |
| Horizontal, top, and bottom padding | Space between the panel edge and its contents. |
| Row spacing | Space between the seekbar and the control row. |
| Seekbar size | Thin, Standard, or Thick track. |
| Seekbar position | Above or below the buttons; remote navigation follows that placement. |
| Seekbar inset | Horizontal inset of the track. |
| Seekbar visibility | Shows or hides the normal seekbar. |
| Played, buffered, and unplayed colors | Independent colors for the track's three portions. |
| Chapter markers | Show or hide chapter positions. |
| Chapter marker color, shape, and size | Color, Ticks or Dots, and marker sizing. |
| Current chapter emphasis | Emphasizes the current chapter marker. |
| Scrubber size, shape, glow, and color | The seek position indicator: Small/Standard/Large; Ring/Solid/Diamond/Pill; optional glow; independent color. |
| Time visibility | Shows or hides the playback-time readout. |
| Time mode | Player default, elapsed/total, elapsed/remaining, or remaining only. |
| Time text size | Size of the playback-time text, not the clock. |
| Time position | Places the readout at Start or End. |
| Time presentation | Pill, Outline, or Plain treatment. |
| Time/control gap | Space between the readout and controls. |
| Control alignment | Start, Center, or End alignment of the buttons. |
| Control size / spacing | Button footprint and distance between buttons. |
| Play icon size / other icon size | Independent icon scaling. 100% uses the baseline sizing, not a 100-pixel icon. |
| Focus outline width | Border thickness on the focused button. |
| Focus highlight opacity | Strength of the focused button's background. |
| Focus enlargement | Additional focused-button scaling. |

### Controls And Editor Actions

Reorder the controls and hide optional ones: Previous, Next, Speed, Decoder, Color filters, Stats, Voice Boost, Volume Boost, DRC, Audio Normalization, and PiP. Play, Chapters, Audio, Subtitles, and Settings are protected. Settings/PiP placement also depends on **Place Settings and PiP in player bar**.

PiP is hidden by default in the player bar. Moving the actions into the bar does not automatically enable PiP; turn it on in the Controls tab.

Editor actions include copying the title or clock panel's surface to the bar, undo/redo, reverting this editing session, and preset import/export. Surface copying leaves geometry and control order alone.

## Clock And Title Customization

The visibility switches are in Player UI settings and the drawer. The live editor styles the season/episode line, show/movie title, episode title, and clock elements separately.

| Visibility setting | Behavior |
| --- | --- |
| Show media title | Displays the title overlay. Default on. |
| Show clock overlay | Displays the clock and end-time estimate with the controls. Default on. |
| Show date with clock | Adds a date line. Default off. |
| Show clock while paused | Keeps the clock visible after controls hide while paused. Default off. |
| Show title while paused | Keeps the title visible after controls hide while paused. Default off. |
| Use 24-hour time | Forces 24-hour formatting. When off, the device time format is used; off does not force 12-hour time. |

Select the part to edit before adjusting the following controls. Part-specific controls are available where applicable.

| Editor setting | What it changes |
| --- | --- |
| Font, size, weight, italic | Typography for the selected element. |
| Letter spacing | Space between characters. |
| Color / opacity | Text color and transparency. |
| Text case | Original, uppercase, lowercase, or title case. |
| Position | Reorders the selected text element within its group. Use Content alignment for alignment inside the panel. |
| Long text | Default handling, Wrap, Ellipsis, or Marquee. |
| Maximum lines | Line limit for long text. |
| Wrapped line spacing | Distance between wrapped lines. |
| Horizontal / vertical text offset | Fine positioning of the selected text. |
| Metadata format | Worded, padded, or compact season/episode formatting. |
| Shadow / shadow strength | Shadow treatment and intensity. |
| Outline thickness / color | Border around the letters. |
| Background plate / strength | Background immediately behind the text, separate from the enclosing panel. |
| Panel mode | Separate title and clock panels, or a combined panel. |
| Panel surface, opacity, accent strength, gradient | Background treatment of the relevant panel. |
| Panel outline / outline width | Panel border and thickness. |
| Corner radius / elevation | Panel shape and shadow. |
| Horizontal / vertical padding | Inner spacing around content. |
| Content spacing | Space between the panel's text rows. |
| Panel alignment | Where the panel sits horizontally. |
| Content alignment | Where its contents align inside it. |
| Panel width | Width of the enclosing panel. |
| Panel vertical offset | Vertical placement of the panel. |
| Visibility | Shows or hides the selected text element. |
| Separator | Changes the season/episode separator: dot, dash, bar, slash, or none. |
| Move panel / Reset position | Positions the title, clock, or combined panel with the remote, or restores its default position. |
| Reset selected part / Reset all | Restores one element's styling or the whole title/clock style. |

Built-in and saved presets can be applied from the editor. Editor actions also copy title appearance to clock or clock appearance to title, copy the player bar surface, and import/export presets. Appearance copying does not copy visibility or positions.

## Title Lookup

Open **Settings > Player UI > Title lookup**.

| Setting | Behavior |
| --- | --- |
| Look up titles with TMDB | Optional online title correction; default off. Uses cleaned titles with season/episode context, or a known movie year. |
| TMDB API Key | Enter your TMDB API key. Saving it triggers a verification request. A rejected key and a network verification failure are different outcomes. |

Lookup uses your own credential, not an app-wide key. A matching episode can replace an incorrect local episode name. If no clear match is found, the local parsed title is retained. Episode ordering and season numbering can differ between a release and TMDB, so a lookup is not a guarantee of the correct episode.

Without lookup, filename cleanup and Anitomy-ng parsing still work locally. They remove recognized release information but cannot invent an episode name that is absent from the source. Title text shown by the app is not proof that an online lookup succeeded.

TMDB lookup sends the cleaned title and available year/season/episode context to TMDB. The credential is stored on the device and excluded from mpvNova's full backups and support exports. Do not post it in screenshots or bug reports.

## Subtitles

Open the player's **Subtitles** panel for tracks, **Open external subtitle**, and **Subtitle track & size**. The language defaults and automatic-selection preferences are also in **Settings > General**.

| Control | Behavior |
| --- | --- |
| Default subtitle language | Preferred language for automatic selection. |
| Prefer forwarded external subtitles | Gives subtitles supplied by a launching app priority over embedded subtitles. Default off. |
| Prefer forced and Signs & Songs | With preferred-language audio, automatically selects forced or Signs & Songs subtitles; otherwise leaves full subtitles off. Default on. Full subtitles remain manually selectable. Foreign-language audio retains normal subtitle selection. |
| Primary / secondary track | Selects the main subtitle and an optional second subtitle. |
| Swap primary and secondary | Exchanges the selected tracks. |
| Size, position, and delay | Adjusts subtitle scale, vertical placement, and synchronization through the custom panel. Secondary controls are separate where offered. |
| Persist subtitle settings | Keeps subtitle tweaks and remembered manual track choices for following files. Matching uses language and track identity/title information rather than assuming track numbers stay the same. |

A manual choice can override automatic selection. Remembered choices depend on matching available tracks; missing or inconsistently labeled tracks cannot always be matched. Forced flags and Signs & Songs labels are hints, not an analysis of the subtitle dialogue.

## Subtitle Customization

Open **Subtitle customization** from the subtitle controls. **Enabled** turns the custom style on or off. The separate preview is a sample of your style, not a full rendering of every authored ASS effect or an image subtitle.

### Text

| Setting | What it changes |
| --- | --- |
| Font | Subtitle typeface, including imported fonts. |
| Font size | Base subtitle size. Player default clears the explicit size override; pressing OK on the size value resets it directly. |
| Text color / text opacity | Text color and transparency. |
| Letter spacing | Space between characters. |
| Font hinting | Player default, Light, Normal, or Native font rasterization hints. |
| Bold / Italic | Text emphasis. |

### Edges And Background

| Setting | What it changes |
| --- | --- |
| Edge style | Selects the outline/shadow treatment. |
| Outline color, size, and opacity | Appearance of the text border. |
| Blur | Softens subtitle edges. |
| Shadow size, color, and opacity | Appearance of the shadow. |
| Background color / opacity | Background behind the subtitle text. |

### Layout

| Setting | What it changes |
| --- | --- |
| Scale | Multiplier on subtitle size, separate from the base font size. |
| Line spacing | Space between lines. |
| Side margin | Horizontal space kept free at the sides. |
| Alignment | Placement of the subtitle block. |
| Justify | Alignment of individual lines inside that block. |
| Position | Vertical placement. |

### Advanced And Presets

| Setting / action | Behavior |
| --- | --- |
| Apply to advanced (ASS) subtitles | Applies your style to ASS/SSA. It can change signs and typesetting. |
| Keep inline typesetting | Keeps authored positioning and inline tags while applying style definitions. Named sign styles can still inherit your chosen font/colors. |
| Force on all subtitles | Strips authored text styling so your style can apply more broadly. Can remove sign positioning and carefully authored layouts. |
| Image subtitle grayscale | Makes image-based subtitles grayscale; text font settings cannot restyle bitmap lettering. |
| Add / remove font | Manages imported subtitle fonts. |
| Preset selection / Save as preset | Applies or saves a subtitle style. |
| Edit / delete preset | Manages saved user presets. |
| Reset style | Returns the custom style controls to their defaults. |

The three ASS choices are override modes, not three effects to stack together. Embedded and external text subtitles can be styled; how much changes depends on their format and selected override mode. Try the least aggressive ASS mode first when preserving signs matters.

## Audio Tools

Open the player's **Audio** panel. These are audio-processing tools, not separate dialogue stems: they cannot perfectly isolate speech from every soundtrack.

| Control | Behavior |
| --- | --- |
| Voice Boost | Speech-focused presets: Speech +4, +6, +8, +10, and Speech max. |
| Volume Boost | Raises overall gain, including music and effects. |
| Channel Downmix | Dialogue-weighted surround-to-stereo mixing, with Center 72%, 84%, 96%, 104%, and 110% presets. Requires a suitable multichannel source to be active. |
| Center Boost | Raises the center-channel contribution on supported multichannel audio. |
| DRC | Reduces differences between quiet and loud passages. |
| DRC+ | An alternative compression/limiting setup; its perceived volume can differ from DRC. |
| DRC Voice | Dialogue-focused compression and limiting. |
| DRC Voice High | Stronger version of DRC Voice; last in the DRC list. Reduce it if voices become harsh or the mix sounds unnatural. |
| Audio Normalization | Loudness presets from -18 through -14 LUFS, plus Loudnorm -22 LUFS. Less-negative targets are louder. |
| Persist filters | Keeps the current filter setup for the next file. |
| Audio delay | Adjusts audio synchronization. |

DRC and Audio Normalization are mutually exclusive in the panel. Turn one off before selecting the other. Processing applies to decoded audio, not an untouched compressed passthrough bitstream. Start with one tool at a time and avoid stacking boosts unnecessarily.

## Network And Buffering

Open **Settings > Network**, or the player's **Network** drawer tab. The controls are shared and saved across streams.

| Preset | Forward / rewind buffer | Buffer ahead | Refill wait |
| --- | --- | --- | --- |
| Default | Original app or mpv.conf values | Original value | Original value |
| Low memory | 32 / 8 MiB | 30 seconds | 2 seconds |
| Balanced | 64 / 16 MiB | 120 seconds | 3 seconds |
| Unsteady connection | 96 / 16 MiB | 180 seconds | 5 seconds |
| High-bitrate video | 256 / 32 MiB | 180 seconds | 3 seconds |

Non-default presets enable waiting for the buffer to refill. Values are limited by the device's memory budget; the largest preset may be reduced on 32-bit or low-memory devices. Individual changes display as Custom. Default removes the app's network overrides rather than replacing your mpv.conf with a hard-coded preset.

| Control | Purpose and timing |
| --- | --- |
| Video buffer size | Forward cache memory limit (`demuxer-max-bytes`). Live in the drawer. |
| Rewind buffer size | Already-played data retained for backward seeking (`demuxer-max-back-bytes`). Live. |
| Buffer ahead | Target cache duration (`cache-secs`). Live. The memory cap can be reached before the time target. |
| Wait for the buffer to refill | Whether playback pauses for rebuffering (`cache-pause`). Live. |
| Refill before resuming | Buffered time required before resuming (`cache-pause-wait`). Live. |
| Local file read-ahead | Read-ahead duration (`demuxer-readahead-secs`). Live. Network caching uses the larger of this and Buffer ahead within memory limits. |
| Stream read buffer | Low-level I/O buffer (`stream-buffer-size`). Applies when a stream is opened again. |
| Connection timeout | Network timeout (`network-timeout`). Applies when a stream is opened again; protocol support varies. |

Numeric controls offer suggested values, custom values, and Default. More cache can absorb brief stalls; it cannot fix insufficient sustained bandwidth, an overloaded decoder, or incorrect HDR rendering.

## Decoders And Device Compatibility

Open **Settings > Advanced** for startup preferences, or the player decoder picker for the current playback path.

### Decoder Modes

| Mode / setting | Behavior |
| --- | --- |
| Automatic decoder fallback | Allows mpvNova's known-problem fallback rules to override the selected path temporarily. Default on. This is different from mpv's Auto mode. |
| Preferred decoder mode | Startup choice used when automatic decoder fallback is off. |
| Auto (safe) | Uses mpv's safe automatic hardware-decoding selection and software fallback. |
| HW+ | MediaCodec direct hardware path, available on supported Android versions. |
| HW | MediaCodec copy hardware path. |
| SW | Software decoding on the normal renderer. |
| G-NEXT Copy | gpu-next rendering with MediaCodec copy decoding. |
| G-NEXT Direct | gpu-next with the direct MediaCodec path. Disabled on NVIDIA Shield because of the purple-screen incompatibility. |
| G-NEXT SW Hi10P fallback | Manually selects gpu-next software decoding with the selected Hi10P tuning. It is a manual decoder choice, not an anime detector. |
| Custom / mpv.conf | Uses the decoder options from your configuration. |
| Use gpu-next | Chooses the libplacebo-based rendering backend where the active decoder mode does not override it. |
| Hardware decoding | Enables the ordinary hardware-decoding preference; explicit modes can override it. |

The bar badge follows the active path, for example G+SW when gpu-next is software-backed. A renderer and a decoder are different components: software decoding can still use gpu-next to render the result.

### Device Toggles

| Setting | Behavior |
| --- | --- |
| Hi10P fallback on this device | Automatically uses G-NEXT SW for 10-bit H.264 when mpvNova's automatic fallback is enabled. Defaults on for Shield, off elsewhere; saved choices persist. Does not mean all 10-bit codecs use this rule. |
| Hi10P fallback tuning | No tuning, Light tuning, or frame-drop tuning. No tuning is the default. Light tuning skips the non-reference deblocking filter, adds a 1-second audio buffer, and uses Lanczos-sharp upscaling. Frame-drop tuning adds late-frame dropping. Revert to no tuning if synchronization or frame delivery worsens. |
| MPEG2 software fallback on this device | Uses G-NEXT software decoding for MPEG2 when the applicable fallback path is active. Defaults on for Shield, off elsewhere. |
| Pause Hi10P while controls are open | Pauses Hi10P while the controls are visible to reduce contention. Defaults on for Shield, off elsewhere. Independent of the general Pause when controls show toggle. |
| Bottom-edge artifact cleanup | Default Off. Automatic uses the Fire TV workaround; manual 8/16/24/32-row choices crop the bottom on this device across videos. This hides an edge artifact, not its decoder cause. Existing independent custom crops are left alone. |

### Dolby Vision FEL

**FEL decoding is disabled by default, not Dolby Vision as a whole.** For interleaved Profile 7 sources, the patch prevents the extra enhancement-layer decoder from starting and removes enhancement video packets while retaining the base video and RPU metadata. Separate enhancement tracks are not automatically paired when FEL is disabled.

This does not perform DV7-to-DV8.1 conversion or add Dolby Vision output capability to a display or rendering path.

There is **no FEL switch in the app settings**. The old FEL and Dolby Vision processing switches were removed. To restore upstream enhancement-layer decoding intentionally, open **Settings > Advanced > Edit mpv.conf** and add:

```ini
vd-lavc-dovi-fel=yes
```

Reopen the video after changing it. Remove the line or set `vd-lavc-dovi-fel=no` to return to default-off. An old saved Android FEL preference does not enable it; this is now a native configuration option. Keep it off on devices where enabling it causes playback failures.

For separate-track sources whose RPU exists only in the enhancement track, skipping that track leaves base-layer playback without that metadata. Other Dolby Vision profiles are not targeted by this Profile 7 packet filter.

## Video Adjustments And Shaders

Open **Settings > Video**, or the video/advanced controls in the player.

| Control | Behavior |
| --- | --- |
| Color filter presets | Applies a preset combination of video adjustments. |
| Brightness, contrast, gamma, saturation | Individual picture adjustments; use the panel's persistence option to keep them where offered. |
| Upscaling / downscaling filter | Chooses the scaler for enlarging/reducing video. More demanding choices can reduce performance. |
| Debanding | Reduces visible color banding with the selected processing mode. |
| Interpolation / temporal interpolation filter | Frame interpolation and its filter. Availability and cost depend on rendering mode. |
| Low-quality video decoding | Trades significant image quality for decoding speed. Default off. |
| Shaders: Enabled | Master switch for the managed shader list. |
| Import file / folder | Adds shaders to the app-managed library. |
| Refresh folder | Rechecks a remembered import folder. |
| Per-shader toggle / order | Enables individual shaders and controls their application order. |
| Remove shader | Removes an entry from the managed collection. |

Managed shaders update during playback when supported by the active path. Shaders configured separately in mpv.conf remain separate from this collection. Their contents determine performance and visual effects; a powerful shader can overload a TV device even if decoding itself is fast.

## Skipping, Seeking, And Resume

Open **Settings > General**, or the player's playback controls.

| Setting | Behavior |
| --- | --- |
| Skip intro and outro | Off, automatic skipping, or a manual skip button. Uses intro/outro/recap timestamps supplied by a launching app; it does not analyze video to discover segments. |
| Skip button duration | Shows the button throughout the segment, or for 10 or 30 seconds. |
| Seek step | Seconds per Left/Right press. Default 10 seconds; presets 5/10/15/30, or custom 1-3600 seconds. Holding accelerates seeking. |
| Fast (keyframe) seeking | Faster but less precise seeks. Default off, which follows the original/configured behavior. |
| Respect input.conf key bindings | With controls hidden, sends Left/Right to mpv instead of mpvNova's built-in seek handling. Default off. |
| Playlist exit confirmation | Confirms exit when a playlist is loaded. Default on. |

Automatic skips display a notification. After rewinding into an already-skipped segment, the app offers a skip button instead of automatically skipping it again.

The nearby Save position on quit, Play new files immediately, background-playback, and popup-playback preferences are inherited playback controls. Resume behavior also depends on launch information from the calling app; an automatic intro skip is not the same thing as restoring a saved position.

## Screensaver

Open **Settings > General > Screensaver**.

| Setting | Behavior |
| --- | --- |
| Mode | Idle behavior while paused, including dimming and the logo screensaver choices offered by the app. Default Dim. |
| Screensaver idle time | Delay before activation while paused. Default 10 minutes. |
| Screensaver logo | Uses the mpvNova logo or an imported custom image. |
| Colour-shift the logo | Changes color on bounces. Default on; disable to retain an imported image's original colors. |

Any remote button wakes the screensaver.

## Updates, Backups, And Support

| Setting / action | Behavior |
| --- | --- |
| App updates / Check for updates | Checks published GitHub releases. The updater chooses a compatible APK using Android compatibility and device ABI, then hands installation to Android. |
| Release history | Shows published release notes. |
| Copy debug info | Copies app, native library, Android, device, and decoder information for troubleshooting. |
| Export config bundle | Exports configuration and support diagnostics. Review the contents before sharing; configuration can contain private URLs or credentials you added yourself. |
| Export full backup | Backs up preferences and supported user files, including config files, fonts, screensaver artwork, and managed shaders. TMDB credentials are excluded. |
| Import full backup | Replaces the current installation's supported settings and user files with the backup. |
| Show stats | Selects the playback-statistics display. This is useful for checking the actual codec/decoder and dropped frames. |
| Enable OpenGL debugging | Extra graphics diagnostics; normally leave off. |
| Ignore audio focus | Prevents normal pausing/ducking when another app requests audio. Normally leave off. |
| Edit mpv.conf / Edit input.conf | Advanced native playback options and key bindings. Incorrect options can override or conflict with UI settings. |

Update prompts are kept out of active playback. A missing compatible APK is reported rather than silently selecting an incompatible build; that does not ask you to uninstall the app.

For a playback report, include the build, device, decoder badge, codec/profile, and a support bundle if possible. Do not assume that buffering, decoding, and rendering problems have the same cause.
