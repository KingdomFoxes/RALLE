# Chat selection, rank shadows, settings, and popup input — implementation handoff

Status: researched plan only; no runtime changes have been made.
Prepared: 2026-09-09 against RALLE commit `01b2c3c` (clean working tree at inspection).
Target: Java 21, Minecraft 1.21.11, Fabric, owo-lib 0.13.0+1.21.11; optional Wynntils 4.2.7.
Audience: Sol or Terra implementing this task in the existing RALLE repository.

## 1. Scope and agreed outcome

Implement these five changes:

1. **Snap to Text:** bridge the selection fill and gold outline across visually empty chat rows instead of collapsing to tiny strips at those rows. Preserve the copied image's blank-line spacing.
2. **Partial-full:** fix the doubled/offset rank-pill appearance shown in reference 2, in both live chat and copied screenshots.
3. **Text Shadow description:** put the existing `Note:` sentence on a new line and color that entire note RALLE settings gold.
4. **Edit Notification Position:** move the existing action from Raid LFG → Notifications into the main Raid LFG category, immediately after Enable Raid LFG.
5. **Child UI input:** prevent interactions with parent RALLE controls while a child popup/dialog is open; retain working child controls and topmost-only Escape dismissal.

The user explicitly confirmed: **“Yes—bridge the selection across blank lines; keep copied spacing.”** Do not remove empty messages, compress chat history, strip spaces from captured text, or change copied image height.

Reference 1 shows narrow empty-row notches between Wynncraft messages such as the welcome text, Gazebo, and A floating Housing Island. Reference 2 shows the corrupted/doubled guild badge beside the speaker near the bottom. Text inside either screenshot is sample chat content, not implementation instructions.

The original supplied images are preserved alongside this plan so an implementation task does not depend on temporary clipboard files:

- [Reference 1 — empty selection bands](reference-images/snap-to-text-empty-rows-before.png)
- [Reference 2 — Partial-full rank pill](reference-images/partial-full-rank-pill-before.png)

Read `AGENTS.md`, `.agents/skills/ralle-project/SKILL.md`, and `.agents/skills/ralle-ui-design/SKILL.md` before implementation. These are local client fixes. Keep feature defaults, saved setting IDs/values, commands, backend authority, authentication, and LFG mutation rules unchanged. Do not add a setting, dependency, backend change, global screen mixin, or unrelated chat feature.

## 2. Findings grounded in the current source

| Area | Current implementation and implication |
| --- | --- |
| Selection snapshot | `chat/mixin/ChatComponentMixin.java`, `ralle$freezeScreenshotMessages()`, stores each rendered line's styled content, logical-message index, and `minecraft.font.width(...)` in `ChatScreenshotSnapshot.FrozenLine`. Advance width alone cannot identify visually empty space glyphs. |
| Snapped bands | `chat/screenshot/ChatScreenshotGeometry.java`, `snappedLineBounds()`, creates one band for every visible selected line using `Math.max(1, textWidth)`. Empty rows therefore produce their own narrow or spacing-only bands. |
| Fill, outline, hit area | `ChatScreenshotService.currentVisualBounds()` feeds `renderLocalFill()`, `renderOutline()`/`drawContour()`, and `previewContains()`. Fix their common geometry, not just the dashed drawing code. |
| Copy | `ChatScreenshotService.copyPreview()` passes every selected rendered line to `TransparentChatCapture`. `captureVisualHeight()` is based on that unchanged line count. Snap currently changes horizontal crop, not vertical spacing. |
| Partial-full | `ChatGraphicsTransform.shadowStyle()` forces `0xFF000000` onto every style, including styles with `withoutShadow()`. `TransparentChatCapture.renderLines()` duplicates that policy. Both paths must be fixed together. |
| Rank composition | `chat/rank/GuildRankGlyphs.java` and `GuildRankTitleTransformer.java` deliberately compose one pill from background and foreground glyph runs with spacing controls. Their two passes are necessary. Do not remove one as a supposed duplicate. Known fonts are `minecraft:banner/pill` and `ralle:guild_rank_star`. |
| Shadow configuration | `ChatBehaviorService.parseTextShadow()` maps saved `full` to **Partial-full** and `wrapped-full` to **Full**. Preserve those values. |
| Settings copy | `assets/ralle/lang/en_us.json` currently puts the note in the same description string. `RalleSettingsScreen.entryRow()` renders a single muted description label. The enable toggle's description is intentionally blank. |
| Settings location | `settings/RalleSettings.java` puts the position action last in the notifications subcategory. Its existing dependency is `raid-lfg-enabled`; its editor target is `LfgNotificationOverlay.ELEMENT_ID`. |
| Dialogs | `RalleSettingsScreen.showModal()` and both LFG modal constructors call `UIContainers.overlay(content).closeOnClick(false)`. Settings color/alias/confirmation dialogs reuse `showModal()`. |
| Proven click-through cause | Installed owo `OverlayContainer.onMouseDown()` returns `false` for an unhandled background click when `closeOnClick` is false. `ParentUIComponent.onMouseDown()` then continues through lower siblings. Closing-on-background and consuming-background-input are separate concerns. |
| Focus and Escape | owo's default `FocusHandler` cycles all root descendants. Its root also dispatches drag/release/key/character events to the focused component. `BaseOwoScreen` has an early greedy-input path. RALLE's existing `RalleModalDialogs.dismissTop()` only removes the last direct overlay/dropdown; it does not isolate input or restore focus. |

Java paths in this document are relative to `src/main/java/org/kingdomfoxes/ralle/` unless they begin with `src/`. Resource paths are relative to `src/main/resources/`.

The rank-shadow mechanism is a strong source-based diagnosis, **not a completed in-game reproduction**. The screenshot cannot reveal effective font IDs or style shadow values. The reproduction gate in section 4 is required before claiming that bug fixed.

## 3. Snap to Text: keep vertical space, bridge empty bands

### Behavior contract

- Logical-message selection stays atomic, including wrapped lines and blank rows within a message. Keep `selectedLines()` and `messageAt()` semantics.
- Preserve original rendered content, row positions, message order, width measurements, horizontal alignment, and copy request line count.
- A visually empty row contributes vertical height but does not choose a new narrow contour width.
- For an internal or leading run of empty rows, use the width of the **next nonempty selected row in screen order (top to bottom)**. This makes the empty run merge into the next actual line, as requested.
- For trailing empty rows with no following nonempty selected row, use the preceding nonempty selected row's width.
- “Next” means visual order, not increasing snapshot index or drag direction. Bottom-up chat reverses snapshot order for this purpose.
- A completely empty selection remains selectable and copyable: use one rectangular fallback with the existing selection/crop width and full selected height. Do not silently cancel it or return no hit area.
- Only selected rows may supply a width. Resolve widths before viewport clipping so a selected nonempty row just outside the viewport can anchor visible blank rows; scrolling must not arbitrarily change their width.
- Apply the same resulting bands to fill, dragging outline, dashed preview, copied fade, and double-click hit testing. Clicking the filled bridge must count as clicking the selection.
- Preserve existing two-GUI-pixel top/bottom padding, viewport clipping, solid/dashed treatments, 120 ms expansion, and 250 ms copied fade. Apply padding to the whole selection, not every empty run.
- Snap off retains its current full-width geometry. Do not trim indentation, recenter Wynncraft announcements, or implement character-tight left edges in this task.

Example, in visual order (numbers are illustrative content widths):

```text
Original rows:       text 80 | blank | blank | text 140 | blank
Contour widths:          80 |   140 |   140 |      140 |   140
Captured rows:       all five, with their existing baselines and spacing
```

### Implementation steps

1. Extend `ChatScreenshotSnapshot.FrozenLine` with immutable visual-content metadata, such as `boolean hasVisibleContent`. Keep `textWidth` unchanged. Update all constructors/fixtures; do not silently infer the flag from width in production.
2. Measure visual content once while freezing the snapshot. Prefer a small Minecraft-facing helper in `chat/screenshot/` using the pinned public font API:

   ```java
   font.prepareText(content, 0, 0, 0xFFFFFFFF, false, false, 0)
   ```

   The two booleans are `drawShadow` and `includeEmpty`; the last argument is background color. With empty glyph areas excluded, `PreparedText.bounds()` or a `Font.GlyphVisitor` can identify whether drawable glyphs/effects were emitted. Confirm the chosen method with the local source and runtime fixtures. Explicit style shadows are still honored by Minecraft even when `drawShadow` is false; for this classification use a temporary style-preserving sequence with only shadow disabled if necessary. Never replace the frozen original sequence.
3. Treat ordinary spaces, NBSP, and resource-pack **space-provider** glyphs as empty when they emit no visual glyph/effect. Preserve underlined/struck-through spaces when they actually draw. Treat visible private-use icons, badges, and zero-advance visible glyphs as content. Do not blacklist all Unicode private-use/format characters or rely on `String.isBlank()`, `font.width() == 0`, or pixel readback. This is geometry preparation, not a framebuffer operation.
4. Change `snappedLineBounds()` to build selected rows in visual order, assign each blank run its donor width, then calculate visible bands. A reverse scan finds the following nonempty donor; a forward fallback handles trailing runs. Use the existing alignment/padding functions for each effective width.
5. Retain all selected row slots; optionally coalesce adjacent equal-left/equal-right bands. Keep bands sorted, gap-free, and nonoverlapping: each `previous.bottom == current.top`. Preserve the existing fractional-scale rounding discipline.
6. Integrate through `currentVisualBounds()`. Preserve its animation-range clipping; verify expansion and contraction across empty runs. Resolve the fallback through existing geometry rather than duplicating widths in input/render code.
7. Leave `copyPreview()`, capture order/height, and the existing maximum-width crop contract intact. Blank-row metadata controls the selection bands only; it is not a filter on the copy request.
8. Update the Snap to Text localization description to mention bridging empty rows while preserving spacing. Add a short behavior note near the geometry implementation and in the existing screenshot documentation.

### Focused verification

Extend `ChatScreenshotGeometryTest` with real empty/nonempty flags and assert rectangles, not only band counts:

- One blank row, several blank rows, positive-width spaces, leading/trailing blanks, and an all-empty selection.
- Following line wider and narrower than the preceding line, under both alignments and both message directions.
- A blank wrapped row inside one logical message; selecting backward produces the same geometry.
- Donor outside the viewport but inside the selected range; unselected text never becomes a donor.
- Fractional chat scale (existing 0.75 case), nonzero viewport origin, nonzero scroll, clipped endpoints, and no internal padding seams.
- Filled bridge hit testing and copy-line preservation. Add a narrow test seam around geometry/copy-request construction if needed; do not boot an entire Minecraft client just to test pure data.

Use an in-game fixture with ordinary spaces, a space-provider glyph, visible private-use glyphs, and decorated spaces to validate the font-based classification. Freeze once; do not remeasure all chat lines every frame.

## 4. Partial-full rank pill: one correctly composed badge

### Reproduce and establish the narrow exception

1. Reproduce reference 2 with the Wynncraft resource pack, Custom Text Shadow enabled, and Partial-full selected. Compare live chat and transparent copy against Vanilla and None. Exercise `titles`, `stars`, and `stars-and-titles`; include an unmodified Wynncraft badge with RALLE internal-rank lookup off.
2. Inspect the effective styles entering the render transform for the badge background, foreground, star run, spacing controls, and adjacent speaker/body text. Use temporary local instrumentation or a debugger; retain a small synthetic styled fixture, not logs of players' chat.
3. Verify whether forcing a shadow on the composed badge causes the extra offset appearance. The pinned Minecraft `Font.PreparedTextBuilder.getShadowColor()` honors explicit style shadow values; RALLE currently overwrites them unconditionally. If the reproduction exposes a different source of duplication, trace the emitted sequences before changing rank encoding.

### Intended fix

- Introduce a small shared render-style helper, for example `chat/ChatTextShadowStyles.java`, used by `ChatGraphicsTransform` and `TransparentChatCapture`. Move the Partial-full policy/constant there so copied output cannot retain the bug after live chat is fixed.
- Ordinary text keeps its opaque black offset shadow in Partial-full, including the existing behavior for ordinary text that previously opted out. Do not globally preserve every `withoutShadow()` value: that would change the existing Partial-full contract and test.
- Known composite rank-badge runs should not acquire an extra forced offset copy. Use a narrowly identified badge-font/run exception and render those runs without the added shadow. Begin with the actual effective `minecraft:banner/pill` and `ralle:guild_rank_star` fonts confirmed by the fixture; apply the same rule to both necessary pill passes and the star foreground.
- If the server fixture uses an inherited/default font for a badge, recognize the confirmed complete badge run using the existing rank-envelope/glyph knowledge or add a narrow render-side annotation. Do not match arbitrary private-use characters, all custom fonts, black text, usernames, or translated rank names. Do not gate correctness on successful Fox lookup.
- Preserve glyph sequence, advance/negative spacing, color, bold/italic/obfuscated/decorated flags, font, click event, hover event, and insertion. Do not strip rank stars or the foreground/background pair, regenerate textures, change title encoding, or make a network request.
- Keep `NONE`, `VANILLA`, and the separate Full halo collector/fallback unchanged. Preserve saved `full`/`wrapped-full` values.
- Call the shared sequence policy from capture's Partial-full branch; remove its duplicate unconditional lambda/constant. Prefer an identical shared entry point for both paths over two similar fixes.

### Focused verification

- Retain `ChatGraphicsTransformTest.partialFullShadowForcesOpaqueBlackWithoutChangingInteractions()` for **ordinary** text; its current `withoutShadow()` fixture is intentional.
- Add badge-font fixtures for background, foreground, stars, and spacing, followed by normal body text. Assert the badge exemption, unchanged ordinary shadow, preserved metadata, identical code points/order, and unchanged measured advance.
- Test that an unrelated custom font and unrelated private-use glyph are unaffected by the exception. Test default/null style shadow and explicit no-shadow badge styles as supported by the reproduction.
- Ensure the shared policy is what capture invokes; compare actual live/copy images in-game. A helper-only unit test cannot prove GPU appearance.
- Run `ChatBehaviorServiceTest`, `ChatGraphicsTransformTest`, and `GuildRankTitleTransformerTest`. Include title-only, star-only, stars+title, Recruit with no star-only pill, and internal Fox titles when available.

Do not report this complete without the visual comparison; if the environment cannot reproduce Wynncraft rendering, explicitly report that verification limit.

## 5. Text Shadow description: newline and settings gold

Display exactly this copy, retaining the existing wording:

```text
Changes the chat text shadow size.
Note: this is most noticeable if 'Chat Text Opacity' is set to 0%
```

The first sentence uses the ordinary muted description color. The **entire second sentence**, starting with `Note:`, uses `RalleTheme.ACCENT` (currently RGB `0xF2B84B`). Use the settings token, not screenshot-outline gold or legacy `§6`.

Suggested small implementation:

1. Change `ralle.settings.option.text-shadow.description` to `Changes the chat text shadow size.\n%s` in `en_us.json`; add `ralle.settings.option.text-shadow.description.note` containing the note sentence.
2. In `RalleSettings.description(...)`, construct this particular description with the unstyled translatable note as its argument. That keeps `SettingsEntry.description()` complete for search and other consumers. Leave all other descriptions on their normal path.
3. Add a small presentation helper used by `RalleSettingsScreen.entryRow()` that supplies a copy of the note argument styled with `RalleTheme.ACCENT`. Compose it through `RalleTheme.ui(...)` so Vanilla and Karla continue to work. Do not introduce owo colors into `api.settings` or parse the English substring `Note:` at render time.
4. Use the same row/helper in normal pages and search results. Preserve note text in narration/search. Retain normal unavailable-row behavior; if this row is ever rendered unavailable, use the existing disabled color for the entire description.
5. Keep `text-shadow-enabled.description` blank. Do not rename modes, alter opacity settings, or rewrite the note's advice in this task.

Manually inspect both fonts at normal and narrow widths. The note must start on a new line, wrap within the description column, remain gold on wrapped continuation lines, and keep the control vertically centered against the full row height. A JSON parse and visual inspection are sufficient for this copy/layout change; avoid source-string assertion tests.

## 6. Move Edit Notification Position

In `RalleSettings.register(...)`, make the main `raid-lfg` entries:

```java
List.of(toggle("raid-lfg-enabled"), action("edit-notification-position"))
```

Remove the action from `subcategory("notifications", ...)`; leave the five notification toggles in their current order. Preserve the dependency:

```java
registry.requireEnabled("edit-notification-position", "raid-lfg-enabled");
```

Keep the action ID, localization, persisted HUD anchor, and `RalleSettingsScreen.openAction()` dispatch unchanged. It still opens the fixed-size notification anchor in the HUD editor and returns to the same settings page/scroll state. Keep Show all's existing editing scope.

Update the existing `RalleSettingsTest.registersApprovedSubcategoryOrderAndUnboundKeyDefaults()` expectation. Check exactly one registry occurrence, main-category search breadcrumb (`subcategory == null`), and absence from the Notifications entries. Use the existing `SettingsSearch` and `SettingsPageContent` behavior; no navigation rewrite or saved-settings migration is needed. In-game, verify the action's dependency while Raid LFG is disabled/enabled and returning from the editor.

## 7. Child dialogs/popups must own input

### Required input behavior

- A modal's backdrop consumes clicks without closing the modal. Clicking exposed parent navigation, toggles, buttons, text fields, cards, or empty space must not activate them.
- Child buttons, text fields, color-picker dragging, selection, paste, scrolling, and keyboard navigation continue to work.
- Parent focus does not receive typing, Enter/Space, drag/release, or keyboard navigation while a popup owns input. Tab/Shift+Tab and supported directional focus navigation stay within the top popup.
- Escape dismisses the topmost popup only; the underlying settings/LFG screen remains. With no popup, preserve existing key-capture cancellation, kick-targeting behavior, and normal screen close/return handling.
- A dropdown's outside click may dismiss that dropdown, but the same press must not activate the control underneath. Modal backdrop clicks remain non-dismissing. A dropdown over a modal closes before the modal on Escape.
- A close/confirm event belongs to the child that received it at dispatch start, even if its callback removes the child. Never redispatch that same event to the newly exposed parent.
- Background LFG state updates and already accepted/pending operations continue normally. Input isolation must not create, cancel, or duplicate backend mutations.

### Implementation architecture

1. Add a shared RALLE popup/input owner under `ui/owo/`, for example `RallePopupController`, and a RALLE-owned overlay subclass if useful. Reuse the existing framed-navy content and owo components. Keep the fix local to RALLE screens rather than changing owo globally.
2. The minimum mouse correction is a modal `onMouseDown` that delegates to its child **then returns true even if unhandled**. Do not turn `closeOnClick` back on: that would change dismissal behavior instead of fixing modality. Do not return before dispatching child controls.
3. Install input ownership at a boundary that runs before the full root traverses parent siblings and before `BaseOwoScreen`'s greedy-key path. Have settings and LFG screens use the same controller. Route each event exactly once to the popup that was topmost when dispatch began, and consume it regardless of the child's return value.
4. Account for owo's local coordinates: screen coordinates are absolute, while `onMouseDown`/drag/release receive coordinates relative to their receiving component. Convert once using component `x()/y()`; retain button info, double-click flag, and drag deltas. Test with a nonzero popup origin.
5. Scope focus to the active popup. Save the prior focus when opening, clear/suspend background focus, focus the first appropriate child, and confine click/cycle/directional focus to descendants of that popup. A scoped `FocusHandler` or controller-owned traversal is appropriate; verify its interaction with root drag/release forwarding. Merely consuming backdrop clicks does not fix global Tab traversal.
6. On close, clear focus/drag ownership of removed descendants and restore the previous mounted, usable component. If the parent was rebuilt while the modal was open, use a safe parent focus fallback. Handle removal by Escape, Cancel, Apply/Confirm, asynchronous success, resize/rebuild, and screen disposal through the same lifecycle. Do not leave subscriptions or a blocking flag after close.
7. Preserve ownership for a mouse press through its release. If the popup closes on the press, swallow the associated release instead of delivering it to a newly exposed control. Cancel stale parent drag/key-capture state when a popup is opened; a popup cannot allow a hidden keybind capture to bind the keys typed in its field.
8. Route existing dropdown opening/removal through the same mechanism (settings choices and LFG Status/Raid/Region menus). Support a dropdown on top of a modal without losing its position or changing the underlying draft. Avoid parallel sources of truth where some overlays are managed and others are only discovered by `dismissTop()`.
9. Make `RalleModalDialogs.dismissTop()` delegate to the shared owner, or replace its call sites coherently. Keep exactly one effective Escape handler. Installed `OverlayContainer.mount()` also subscribes to root Escape; prevent that subscription from closing multiple layers after the screen/controller already handled it.
10. In `RaidLfgScreen`, guard `updateKickHover()`, kick hold progression, and kick overlay presentation while child UI owns input. Clear a pending pointer hold on modal opening so it cannot mature into a hidden kick. Do not pause service synchronization or an explicitly started join countdown.

### Call sites to cover

- `RalleSettingsScreen.showModal()`, `keyPressed()`, pointer entry points, choice-dropdown opening, `onClose()`.
- `RalleModalDialogs.confirm()` and its dismissal helper.
- `ConsumableColorDialogScreen` (including the reused Show Who Queued color editor) and `ConsumableAliasDialogScreen`.
- `RaidLfgScreen.openCreateModal()`, `openDisbandConfirmation()`, the three filter dropdowns, input/close handling, and pointer-driven kick code.
- `ChatLayoutEditorScreen` is a separate child screen with a stored parent, not an overlay. Verify close/return isolation and that settings beneath it never receive events; do not replace it with a modal or change its HUD editing gestures.

### Meaningful regressions

Add focused interaction tests using the shared router/overlay and recording child/parent handlers, following the project's JUnit style:

| Scenario | Expected result |
| --- | --- |
| Modal backdrop press above a parent button | Parent activation count stays zero; modal remains open. |
| Unhandled click inside dialog content | Still consumed; parent unchanged. |
| Child button closes modal during press | Child invoked once; parent receives neither the press nor its release. |
| Typing with a formerly focused settings search box | Only the modal field changes. |
| Tab, Shift+Tab, Enter/Space | Focus/actions remain inside the top popup. |
| Drag child color picker outside content, release | Correct child drag lifecycle; no parent slider/editor drag. |
| Scroll over backdrop | No parent scroll movement. |
| Dropdown over modal; Escape twice | Dropdown closes first, modal second, parent screen stays open. |
| Dropdown outside click over parent action | Menu closes; action does not run until a new deliberate click. |
| Close after parent rebuild/disposal | No stale focus, input capture, or event subscription remains. |
| No popup | Existing parent dispatch and key-capture behavior remain. |

Include a behavioral test for background LFG kick hold suppression where practical. Existing `RaidLfgScreenTest` mainly exercises presentation/domain helpers; do not claim those tests establish modal isolation without dispatching input.

## 8. Implementation order and verification

Deliver small, buildable slices in this order:

1. Description formatting and notification-action relocation.
2. Frozen-line visibility metadata and snapped-band bridging, including geometry regressions.
3. Reproduced rank-shadow correction shared by live rendering and capture.
4. Shared popup ownership and migration of all settings/LFG call sites, including input regressions.
5. In-game visual/interaction verification and documentation updates.

Use PowerShell from the repository root with Java 21. Start with focused tests for the changed slice, then broaden once:

```powershell
.\gradlew.bat test --tests '*RalleSettingsTest' --tests '*SettingsSearchTest' --tests '*SettingsPageContentTest'
.\gradlew.bat test --tests '*ChatScreenshotGeometryTest' --tests '*ChatScreenshotLifecycleTest' --tests '*ChatSelectionAutoscrollTest'
.\gradlew.bat test --tests '*ChatGraphicsTransformTest' --tests '*ChatBehaviorServiceTest' --tests '*GuildRankTitleTransformerTest'
# Run the actual names chosen for the new popup and content-measurement/helper tests.
.\gradlew.bat test --tests '*RallePopupControllerTest' --tests '*RaidLfgScreenTest' --tests '*ChatLayoutEditorScreenTest'
.\gradlew.bat build
```

Do not blindly run a suggested test filter whose class was not created; use the final class names. Parse the edited localization JSON. Run `git diff --check` and inspect the final diff for saved-key changes, duplicate actions, missed overlay constructors, and remaining unconditional Partial-full transforms. No version bump or release deployment is requested.

Manual verification should cover at least two GUI scales and both practical wide/narrow settings layouts:

- Reference-like chat with wrapped lines, blank runs, centered/indented announcements, formatted links, and guild badges. Check left/right alignment, top-down/bottom-up order, Snap on/off, and smooth expansion on/off.
- Ctrl-drag in either direction, double-click on an empty-space bridge, Ctrl+C, copy failure/retry, copied fade, manual-scroll cancellation, and edge autoscroll. Paste into a transparency-capable viewer: blank-line positions and image height are unchanged; no gold/fill/confirmation/world pixels appear in the copy.
- Partial-full badge comparison in live chat and copy; ordinary offset shadows remain. Verify Vanilla/None/Full did not regress and hover/click metadata still works.
- Settings gold note in both fonts and search, relocated action/dependency/editor return, color and alias dialogs, confirmation modals, LFG Create/Disband, dropdowns, keyboard focus, and attempts to click every exposed parent area.
- With and without Wynntils where relevant; fresh disabled configuration remains inert. Ordinary local chat and settings validation do not require a backend connection.

Update detailed behavior in `docs/ARCHITECTURE.md`/near the owning implementation and the UI skill's screenshot/settings/popup guidance to reflect these approved changes. Do not rewrite unrelated navigation/style guidance or put a feature checklist in `AGENTS.md`.

## 9. Handoff completion criteria

The implementation is complete when all five outcomes are present, focused tests and the build pass, and the visual/input checks above have been exercised or their unavailable portions clearly reported. State separately what was unit-tested and what was observed in Minecraft. Do not substitute a source-level diagnosis for a verified rank-rendering fix.

This planning task did not run a build, modify feature code, install a jar, or reproduce the bugs in a running client. It inspected both supplied images, relevant Java/resources/tests, and the pinned local Minecraft/owo sources. No product decision remains open for the empty-line behavior; the rank bug's exact runtime style fixture is the remaining diagnostic work for implementation.
