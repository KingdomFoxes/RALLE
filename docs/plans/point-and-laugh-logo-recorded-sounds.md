# Implementation handoff for GPT-6-luna

Prepared 2026-09-25 from the current RALLE checkout. This is a plan, not an implementation report. No application code or runtime assets were changed while preparing it. The attached logo was copied unchanged into `docs/plans/reference-images/ralle-logo-user-2026-09-25.png` so this handoff does not depend on a temporary clipboard file.

## Objective and scope

Implement the user's four requests:

1. Point and Laugh shows gray `Type here...` when its text box is empty and unfocused. Focusing it hides that hint.
2. Its configured text is visible without first clicking the box.
3. Replace the existing logo with the supplied crest.
4. Add realistic recorded Piano and Drums chat-selection options from an external source. Retain the current synthesized sounds as `Piano (Old)` and `Drums (Old)`. The new options have no tag and appear immediately above their respective old versions.

Treat the supplied image as an asset reference, not instructions. Read root `AGENTS.md`, `.agents/skills/ralle-project/SKILL.md`, and `.agents/skills/ralle-ui-design/SKILL.md` before implementing. Recheck the working tree and relevant code because it may have changed since this plan was written. Preserve unrelated user changes. Work in three cohesive slices: text field, logo, sounds. Add exactly two instrument options while retaining all existing banks. Do not change LFG/backend behavior, default feature states, Point and Laugh triggering, or existing sound timing. No publication or release is part of this handoff.

## 1. Point and Laugh field

### Files and current behavior

- `src/main/java/org/kingdomfoxes/ralle/ui/owo/RalleSettingsScreen.java`: `control(...)`, around line 400, creates the `TextSetting` field at 126 × 20, calls `setMaxLength`, `setMessage`, `setValue(setting.value())`, then subscribes `setting::set`.
- `src/main/java/org/kingdomfoxes/ralle/api/settings/TextSetting.java`: empty string default; rejects control characters, section signs, and values over its configured length.
- `src/main/java/org/kingdomfoxes/ralle/settings/RalleSettings.java`: Point and Laugh is the top-level War text setting with a 254-character maximum.
- `src/main/resources/assets/ralle/lang/en_us.json`: existing title and description.
- `src/main/java/org/kingdomfoxes/ralle/war/PointAndLaugh.java` and `docs/ARCHITECTURE.md`, Point and Laugh section: blank text is off; exact server message matching and bounded guild-chat response already exist.

Source inspection found a strong explanation for the rendering defect, but it still needs in-game reproduction. owo `TextBoxComponent` starts with width zero. Minecraft `EditBox.setValue` moves the cursor to the end and scrolls using the current inner width. Rendering later takes a substring from that scroll position. owo's existing `text(String)` helper calls `setValue` followed by `moveCursorToStart(false)`, which resets the initial visible position. This is preferable to a custom renderer or forcing focus.

Minecraft 1.21.11 `EditBox.renderWidget` already draws its hint only when its rendered string is empty and the box is unfocused. Because it tests the rendered substring, fixing the initial scroll position is necessary before trusting the hint with saved text. `setHint` only supplies its default gray style when the Component has no style; a typography component may already have a font style, so explicitly set the hint's gray color.

### Implementation sequence

1. Reproduce with a saved short value and with a 254-character value: open War without clicking the box; note whether the value is present in configuration but invisible on screen.
2. Change initialization in the `TextSetting` branch from `input.setValue(setting.value())` to `input.text(setting.value())`, keeping initialization before the change listener subscription. This normalizes only the initial cursor and scroll position; do not reset the cursor each frame or after every keystroke.
3. For `RalleSettings.POINT_AND_LAUGH_ID`, set a localized hint such as `ralle.settings.option.point-and-laugh.placeholder`, whose English value is exactly `Type here...` (three ordinary periods).
4. Apply the existing typography helper and explicitly style the hint with a readable gray, e.g. Minecraft `ChatFormatting.GRAY`. Preserve the normal entered-text color. Do not make the whole EditBox gray or confuse availability with focus.
5. Keep native EditBox focus, caret, selection, clipboard and narration behavior. Do not put the hint into `setValue`, the saved configuration, or the outgoing guild message. Clicking to focus must not change the value. Keyboard focus should hide the hint too.
6. Check rendering after font/theme rebuilds, category switching, search results, closing/reopening and resize. If resetting initialization does not fix every observed path, inspect the actual lifecycle and scroll position before adding code. Use a narrowly scoped component only if the built-in behavior demonstrably fails; no mixin is expected.

### State contract

| Value | Focus | Appearance |
|---|---|---|
| Empty | Unfocused | Gray `Type here...` |
| Empty | Focused | No hint; native caret |
| Nonempty | Unfocused | Configured text visible, clipped normally if long |
| Nonempty | Focused | Text and native editing behavior |
| Cleared, then blurred | Unfocused | Hint returns; saved value remains empty |

Use actual emptiness for the visual hint. Do not trim user input or change existing whitespace-only runtime behavior. Long values need a useful visible segment, not all 254 characters squeezed into the field. On first display, show the beginning.

### Verification

Run `RalleSettingsTest` and `PointAndLaughTest`; preserve persistence, empty defaults, length validation, exact trigger matching and throttling. Use manual rendered verification for the field states above; do not add a test that merely checks a copied boolean predicate or source-code spelling. If a component lifecycle test is practical in the existing harness, it should reproduce saved-text initialization before layout and assert that text remains visible after layout without focus. Do not claim ordinary JVM tests establish rendered correctness.

Also exercise click-away, paste, select-all, delete, Home/End, ordinary typing, Escape and available keyboard navigation. A placeholder must never appear in the saved setting or enable the feature.

## 2. Supplied logo

### Verified starting point

- Durable source: `docs/plans/reference-images/ralle-logo-user-2026-09-25.png`.
- It is 777 × 1186. Its corner pixel has alpha zero: the apparent black backdrop in the attachment is transparent there. Preserve its existing alpha instead of applying black removal.
- Current header asset: `src/main/resources/assets/ralle/textures/gui/fox_overlay.png`.
- Shared consumer: `src/main/java/org/kingdomfoxes/ralle/ui/owo/RalleHeader.java`.
- `RalleHeader` currently samples a 159 × 232 texture and displays it at 19 × 28 within a 41-pixel-high header, with 10-pixel margins.
- `src/main/resources/fabric.mod.json` currently has no `icon` field.

### Implementation sequence

1. Use the supplied PNG itself as the runtime logo; do not redraw, generate, recolor or simplify the crest. Copying the image is sufficient for the existing header use.
2. Replace `fox_overlay.png` with the supplied image. Retaining the resource path avoids unnecessary resource-reference churn.
3. Update the full-image source dimensions in `RalleHeader` to 777 × 1186. Keep the full crest visible and preserve its aspect ratio. At 28 pixels high, an 18-pixel logical width is a good integer approximation; inspect it in context before settling the final width. Preserve header height, title font, accent and spacing unless a minimal correction is needed to prevent overlap.
4. Search all header consumers and asset references again. Verify settings, Raid LFG and any other screen using the shared header. Fix the shared component rather than making screen-local logo copies.
5. Scope assumption: “update the logo” means the existing in-app logo. Adding a Fabric/Mod Menu icon is optional additional work, not a dependency or acceptance requirement. Do not change remote GitHub or Modrinth branding, publish a release, or invent a new branded background. If a mod icon is explicitly requested later, use an aspect-preserving square derivative and add the metadata field then.

### Verification

Inspect the actual image in-game at two practical GUI scales, alongside the old layout dimensions. Check the crown and bottom ornament are not cropped, the transparency blends into navy, the title does not overlap and the white design remains recognizable. Inspect packaged resources in the built jar. Retain the source PNG so a later size adjustment does not resample an already reduced image.

## 3. Recorded Piano and Drums

### Existing architecture to preserve

- Instrument choices and mapping: `src/main/java/org/kingdomfoxes/ralle/sound/ChatSelectionInstrument.java`.
- Playback: `MinecraftChatSelectionSoundPlayer.java`, in the same directory. UI playback uses pitch 1.0 and volume 0.35; both feature gates remain authoritative.
- Registration: `RalleSoundCue.java`, `RalleSoundEvents.java` and `src/main/resources/assets/ralle/sounds.json`.
- Feedback lifecycle: `src/main/java/org/kingdomfoxes/ralle/chat/screenshot/ChatSelectionSoundFeedback.java`.
- Existing synthesized banks to preserve: `src/main/resources/assets/ralle/sounds/ui/piano/` and `.../drums/`.
- New recorded banks: `src/main/resources/assets/ralle/sounds/ui/piano_recorded/` and `.../drums_recorded/`.
- Existing generator: `tools/generate_chat_instruments.py`.
- Provenance: `src/main/resources/licenses/ralle-sounds/README.txt`.

Preserve existing audio files, cue IDs and event IDs for the synthesized banks. Existing saved choices `piano` and `drums` must continue playing the same sounds, now displayed as `Piano (Old)` and `Drums (Old)`. Use new persisted IDs `piano-recorded` and `drums-recorded` for the recorded choices displayed simply as `Piano` and `Drums`. No configuration migration or automatic switching is needed. Preserve xylophone default, hidden-choice persistence, 40 ms rate limit, latest-count coalescing, silent cancellation/failure and no runtime network access. Leave acoustic guitar, bass guitar, xylophone and LFG sounds unchanged.

### Choices, labels and registration

Use this exact dropdown order, independently of enum declaration order:

1. Xylophone — `xylophone`
2. Acoustic Guitar — `acoustic-guitar`
3. Bass Guitar — `bass-guitar`
4. Piano — `piano-recorded`
5. Piano (Old) — `piano`
6. Drums — `drums-recorded`
7. Drums (Old) — `drums`

Update the choices list in `RalleSettings.java` and localized value labels in `en_us.json`. Keep existing `PIANO` and `DRUMS` enum constants and mappings as the old banks; add `PIANO_RECORDED` and `DRUMS_RECORDED` entries. Add corresponding `PIANO_RECORDED_*` and `DRUMS_RECORDED_*` cues and registrations using `ui.piano_recorded.*` and `ui.drums_recorded.*` event IDs. The new entries use the same note/count and copy-success behavior described below. The `(Old)` suffix is presentation only; do not rename saved IDs to `piano-old` or `drums-old`, or redirect old IDs to recorded audio. Check every exhaustive enum switch and every place that enumerates banks, including tests and preview tooling.

### Sourced candidates, verified during planning

**Piano: Salamander Grand Piano V3 by Alexander Holm.** The maintained library describes real piano recordings with 16 velocity layers and minor-third sampling, and identifies its license as CC BY 3.0. Use this as the first candidate. [Library and license](https://github.com/sfzinstruments/SalamanderGrandPiano), [original archive](https://archive.org/details/SalamanderGrandPianoV3).

**Drums: AVL Drumkits by Glen MacArthur.** The creator provides recorded acoustic kits, downloadable SFZ folders containing individual samples, and a CC BY-SA 3.0 license with additional explanatory terms. Audition Black Pearl first, then Red Zeppelin or Blonde Bop if needed for the required toms and crisp natural resonance. [Creator's page](https://www.bandshed.net/avldrumkits/), [SFZ downloads](https://www.bandshed.net/sounds/AVLDrumkits_SFZ/), [license statement](https://bandshed.net/pdf/AVL-Drumkits%20CC-BY-SA%20License.pdf).

These are researched candidates, not downloaded or auditioned selections. Verify the license files and sample inventory in the exact downloaded version before incorporation. Keep the piano attribution and license, and preserve the drum sample license for the adapted assets. AVL explicitly requires attribution for modified samples/library formats and a different name for a modified library. Use a distinct name such as “RALLE selection drum cues, derived from AVL Drumkits”; preserve creator information and describe processing. Do not claim these recordings are RALLE-original synthesis or relabel them GPL-only. Do not rely on a “royalty free” headline, or use a library that only permits finished music while forbidding sample redistribution. If a candidate proves unsuitable, find another recorded source with explicit sample redistribution terms; do not silently revert to synthesis.

### Required output mapping

Piano has ten selection notes, in this exact order:

| Count | File stem | MIDI note |
|---|---|---|
| 1 | d4 | 62 |
| 2 | e4 | 64 |
| 3 | f_sharp_4 | 66 |
| 4 | g4 | 67 |
| 5 | a4 | 69 |
| 6 | b4 | 71 |
| 7 | c_sharp_5 | 73 |
| 8 | d5 | 74 |
| 9 | e5 | 76 |
| 10+ | f_sharp_5 | 78 |

`copy_success.ogg` combines recorded D4 at time zero and recorded D5 starting 80 ms later. Mix their overlap with headroom; do not concatenate them or introduce another runtime scheduler.

Drums use `bass_drum.ogg`, `floor_tom.ogg`, `low_tom.ogg`, `high_tom.ogg`, `snare.ogg` for counts 1, 2, 3, 4 and 5+ respectively; `crash.ogg` plays once for successful copying. Prefer distinct recorded kit pieces, not one tom pitch-shifted into every slot.

### Asset preparation and reproducibility

1. Download only the necessary source material during development. Record original URL, pinned revision or archive version, checksum, creator, license, source filename and velocity layer for each output.
2. Audition medium-velocity piano samples and a coherent drum kit. Choose clear attacks and natural resonance at restrained UI levels. Use the SFZ mapping to determine actual root pitches; do not assume filenames alone identify octave conventions.
3. Derive missing piano notes from the nearest recorded key with documented minimal pitch shifting/resampling. This is processing recordings, not generating synthetic instruments. No oscillator synthesis, AI-generated audio or new runtime sampler dependency.
4. Remove excess leading silence without clipping the hammer/stick transient. Keep natural decay with a short end fade. Start around 0.5–0.9 seconds for piano, 0.25–0.7 seconds for drum hits and 0.8–1.2 seconds for crash, adjusting by listening. These are working ranges, not hard requirements; avoid abrupt truncation and long muddy overlap.
5. Encode actual OGG Vorbis, preferably mono at a consistent sample rate. Listen after downmixing; reject phase cancellation. Balance perceived loudness across notes and relative to the existing banks at the unchanged playback gain. Leave decoding/mixing headroom; equal peaks alone do not mean equal loudness.
6. Create an offline import/conversion tool or documented reproducible recipe, scoped to the two new recorded directories. Never write into the existing synthesized bank directories. Keep downloads, large source archives and audition WAVs outside runtime resources. Normal Gradle builds and installed clients must work entirely from packaged OGG files, without fetching anything.
7. Keep `generate_chat_instruments.py` capable of reproducing the existing synthesized Piano/Drums and guitar/bass banks. Ensure its cleanup and manifest updates cannot delete or overwrite the new recorded directories or event namespaces. Preserve the shipped old audio byte-for-byte in this change. Add a genuine preview-only path that never invokes generation; currently `--previews` still calls `main()` first. Do not regenerate old banks merely to audition the new ones.
8. Make audition buffer sizing account for the actual decoded clip lengths, especially the longer crash. Existing preview code allocates a fixed 1.3-second final tail; longer recordings can exceed that buffer. Preserve equal playback gain without normalizing each preview separately.
9. Retain all existing manifest references and registered cues, and add the 17 new recorded cues/assets. Update the provenance README so the synthesized `(Old)` banks and the new recorded banks are accurately distinguished. Bundle the applicable attribution and license texts with the jar.

### Audio verification

Extend and run `ChatSelectionInstrumentTest`, `MinecraftChatSelectionSoundPlayerTest`, `RalleSoundAssetsTest` and `ChatSelectionSoundFeedbackTest`. Cover all seven choices, exact dropdown order and labels, new cue mappings, old saved IDs resolving to old banks, round-trip persistence of new IDs, and missing/invalid values still falling back to xylophone. Ensure packaged-asset coverage includes both old and new banks; update hardcoded bank lists and exhaustive switches rather than replacing their old entries. Verify old asset hashes are unchanged. Existing asset tests verify manifest linkage and basic Vorbis markers; additionally decode every new asset using an audio tool to check actual codec validity, sample rate, nonempty signal and clipping. Listen to decoded packaged OGGs rather than only source WAVs.

Produce unshipped comparison/audition files under `build/audio-previews` for both old and recorded variants: all ten piano notes followed by its copy motif, all five drum count cues followed by crash, and rapid 40 ms selection bursts. Verify that sustained overlap is tolerable, recorded piano sounds struck rather than electronic, recorded drums retain distinguishable attacks/toms, and crash is not disproportionately loud. Exercise all four Piano/Drums options, selection, shrinking/expanding counts, cancel, successful clipboard copy and failure in-game. Disabled features remain silent.

## 4. Documentation, checks and completion

Update `docs/ARCHITECTURE.md` near Point and Laugh and the sound section. Document the empty/unfocused hint and persistent visible text, and replace the outdated claim that all banks are procedural synthesis. Document the seven-choice ordering, `(Old)` labels, new saved IDs and preservation of existing saved selections. Update the UI skill's detailed sound guidance to reflect both recorded and retained synthesized Piano/Drums variants while preserving its note/count/timing contract. Add the approved Point and Laugh visual states there if appropriate. Root `AGENTS.md` needs no change.

Suggested narrow checks, run from the repository root in PowerShell with Java 21:

```powershell
.\gradlew.bat test --tests '*RalleSettingsTest' --tests '*PointAndLaughTest'
.\gradlew.bat test --tests '*ChatSelectionInstrumentTest' --tests '*MinecraftChatSelectionSoundPlayerTest' --tests '*RalleSoundAssetsTest' --tests '*ChatSelectionSoundFeedbackTest'
.\gradlew.bat build
```

Run focused checks after each relevant slice, then the broader build after integration. Do not keep repeating passing tests without a new change or concern. Inspect the final diff and built jar for the logo, all 17 additional recorded sound files (11 piano, 6 drums), the preserved original banks, manifest linkage and licenses. Do not commit generated previews, downloaded libraries or unrelated modifications.

Final manual acceptance checklist:

- Fresh empty Point and Laugh: hint shown in gray; click/keyboard focus hides it; blur restores it.
- Saved text appears immediately on screen open and after rebuild, without clicking the field.
- Typing, clearing, long values and persistence remain correct; placeholder never becomes real text.
- Supplied crest is visible in each shared-header screen with correct aspect and transparency.
- New untagged Piano/Drums options play recorded samples with the specified mappings and success timing, each immediately above its corresponding `(Old)` option.
- Piano (Old) and Drums (Old) play the unchanged synthesized banks; previously saved selections still resolve to those banks, and new selections persist across reopen/restart.
- Original banks, disabled gates, 40 ms limit and silent failure/cancellation remain intact.
- Local UI is checked at multiple GUI scales; relevant chat flows are checked with and without optional Wynntils before release.
- Provenance is packaged, regeneration cannot overwrite recordings, and no new backend/runtime downloads occur.

Completion report should list changed files, sample sources and licenses, checks actually run, and any remaining in-game or listening verification. Do not report a successful build as proof of visual or acoustic quality. Provide screenshots/audio previews when available, and explicitly label anything that could not be verified.
