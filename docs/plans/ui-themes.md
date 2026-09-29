# UI Themes implementation plan

Status: implementation plan only, 2026-09-22. Use the existing owo-lib text dropdown with contributor IGNs in parentheses. No runtime changes are part of this planning task.

## Requested behavior

Add a choice setting, not a toggle:

- Name: **UI Themes**
- Description, verbatim: **Palette swaps of the mods three colors that it uses everywhere! Made by people from KoF, see IGNs beside theme names for who made what!**
- Default option label: **Default theme**.
- 25 options: the current default plus all 24 community themes, including Hot Chocolate.
- Contributor options display `{theme name} ({IGN})` in both the open dropdown and the closed choice control, for example `Countx Sini (Nothesinistrtype)`. Default theme displays only `Default theme`. No heads, icon spacing, contributor tooltips or skin loading.
- Proposed placement: About, directly below Interface Font. Theme selection is independent of feature toggles and works outside Wynncraft, without LFG authentication.
- Persist a stable theme ID locally, proposed `about.ui-theme`; never persist an option index, display name or credentials in this setting.
- Apply on explicit selection, without a restart. Merely hovering or focusing another option does not change the theme.

The earlier sheet-only omissions do not exclude those components from implementation. Raid LFG, dialogs and other RALLE-owned UI still participate in global theming even though the requested images omitted their full menus.

## Dropdown approach

Use the same `DropdownComponent.openContextMenu(...)` and `menu.button(...)` path already used by `RalleSettingsScreen.openChoice`. Theme name and parenthesized IGN are ordinary text, supported by the existing API. Keep the current dropdown implementation and interactions; do not introduce a custom dropdown subclass, row component, popup framework or skin service.

Verified against the project's resolved `owo-lib 0.13.0+1.21.11` source: `button(Component, Consumer<DropdownComponent>)` accepts the composed label directly. [owo dropdown documentation](https://docs.wispforest.io/owo/ui/components/dropdown) describes this standard text-entry API.

The stock context menu clamps its position but does not automatically scroll a 25-entry list. Validate the full list and longer credited labels at supported GUI scales early. If the stock menu cannot keep every option reachable, report the concrete limitation and agree on a layout using existing controls before changing the design. Do not silently reintroduce custom dropdown work.

## 1. Define the palette and attribution catalog

Create immutable theme definitions in a toolkit-independent presentation package, proposed `ui.theme`:

- Theme ID, display-name translation key, three submitted RGB values, optional contributor ID.
- Contributor ID and supplied IGN for plain-text credit. No UUID resolution, account lookup or skin metadata is needed. Credits and authentication/party members remain separate concepts.
- An explicitly named stable ID for the unnamed `???` option; keep its display name as submitted until renamed by the user.
- Validate IDs, references and RGB values. Import the values from `colors/themes.json` into packaged definitions; do not read files from the workspace `colors` folder at runtime.
- Use one documented catalog order: Default theme first, followed by the existing submission order. Renaming a label must not change saved IDs.

Use a resolved palette with semantic roles rather than replacing every occurrence of a matching RGB number. Roles include background, frame outline, accent text/outline, inset accent, translucent selection fill, secondary surface, hover/focus, and themed-surface text. Preserve existing alpha values, geometry and timing.

**Default theme must reproduce current runtime colors exactly.** The generated sheets used approximate derived shades; do not treat those shades as the baseline for changing the shipped default. For non-default themes, preserve the submitted background/outline/accent and derive supporting shades consistently. Dark text may be needed on light themed surfaces; fixed gray controls retain readable light text. Do not silently substitute a different submitted accent to fix a low-contrast palette.

## 2. Register and bind the choice

Extend `settings/RalleSettings.java` using the existing `ChoiceSetting` and local `SettingsRegistry` persistence path. Add the exact localized title/description and theme labels to `assets/ralle/lang/en_us.json`. Expose theme metadata in the presentation layer rather than adding owo dependencies to `ChoiceSetting`.

Bind a shared palette resolver after settings load/seal, alongside the existing `RalleTypography.bind(settings)` bootstrap. Resolve the default safely before bootstrap. Unknown or removed saved IDs fall back to Default theme using the registry's load-error handling; verify that fallback rather than introducing a second configuration system.

The existing `Setting` owns one package-private change listener used by persistence. Do not replace that listener. Use a bound value supplier and theme revision/change detection, or extend observer support while preserving persistence, if implementation requires notifications.

## 3. Reuse the existing choice control

Keep the existing `ButtonComponent` trigger and `openChoice` dropdown in `RalleSettingsScreen`.

- Add a small shared choice-label formatter for UI Themes: localized theme name followed by ` (IGN)` when a contributor exists; no suffix for Default theme. Other settings retain their existing labels.
- Use that formatter for dropdown entries and the selected value. The closed control keeps its existing native dropdown arrow.
- Preserve the current Interface Font preference. The earlier Minecraft-only sheet request does not remove the mod's font setting.
- Preserve stock selection, dismissal, hover and focus behavior. Do not add custom head hit regions, skin futures, navigation or scrolling machinery.
- Measure long credited labels, including `Radioactive Robert (ToolyTom)` and `Countx Sini (Nothesinistrtype)`, against the existing control lane and menu bounds. Prefer existing sizing/layout options and report any unresolved clipping or reachability limitation.
- On explicit selection, persist once, close the menu and refresh the settings header/sidebar/content and selected label while retaining search, category and scroll position. Theme hover does not apply a preview.

## 4. Contributor attribution

Use these supplied IGNs: SaltyKing, SpaseCow, **Nothesinistrtype**, Robturne, NeonRider, ToolyTom, maxkarson and _Hotchocolate. The corrected Nothesinistrtype credits Countx Sini. Each contributor's themes repeat their IGN in parentheses.

Default theme has no contributor suffix or icon. Attribution is packaged text; no skin-source decision, external account lookup, cache, downloads or new network requests are involved.

## 5. Migrate every RALLE-owned palette consumer

Audit hardcoded RGB/ARGB values by visual meaning, including call sites that pass pre-styled text. Centralizing `RalleTheme` alone is insufficient because several renderers currently own constants.

| Surface | Principal code to inspect/change | Required behavior |
| --- | --- | --- |
| Shared screens and popups | `RalleTheme`, `RalleSurfaces`, `RalleHeader`, `RalleModalDialogs`, `RalleModalOverlay` | Theme actual surfaces/frames; preserve blur/world backdrop and Fox identity artwork. |
| Settings | `RalleSettingsScreen`, `SettingsSectionDivider`, `SettingsNavigationRailComponent`, `RalleButtonRenderers`, `RalleToggleComponent`, icon controls | Continuous rail, selected text/focus and headings use accent. Existing gray settings rows/controls stay gray; no blanket background recolor of controls. |
| Custom settings content | consumable/alias/color dialogs, hierarchy editor, queue-color preview | Theme UI framing and copy, not saved highlight colors or HSV color-wheel values. |
| HUD editor | `ChatLayoutEditorScreen` | Theme selection fill, outer/inset frames, corner accents and labels, preserving exact move/resize geometry, clamping, gaps and fixed-size distinctions. Keep gameplay visible behind editor elements. |
| Chat screenshots | `ChatScreenshotTokens` and their consumers | Theme drag/preview borders, 90%-opacity selection fill and Copied screenshot confirmation. Preserve animation/alpha and exclude decoration from captured images. |
| Local RALLE messages | `chat/RalleChatMessages` and authored message call sites | Theme prefix/link emphasis while preserving bold, underline, click/hover metadata and normal readable chat copy. Do not recolor server/player chat. |
| LFG browser | `RaidLfgScreen` and modal content | Theme cards, titles, focus, selection, kick connector/hold start and themed copy. Preserve semantic destructive/error states, authorization and command behavior. |
| Notification cards | `LfgNotificationOverlay` | Use the theme's exact BG RGB with existing opacity, themed outline/accent, all persistent/discovery/countdown/timeout/confirmation variants. Preserve head/guild colors and semantic buttons. |
| Selector wheels | `SelectorWheelRenderer`, `LfgSelectorWheelScreen`, `CreateWheelSnapshot` | Theme segment fills/outlines/hover; never add a BG-colored rectangular plate over the blurred backdrop. Preserve Dailies/NOTG/NOL/TCC/TNA/TWP, items, geometry and effects. |
| Action Bar and other RALLE overlays | `LfgActionBarOverlay` and local preview paths | Theme accent/normal presentation as appropriate; retain semantic status colors, glyphs and native/world readability. |

Keep fixed semantic green/red/yellow status colors, region indicators, guild identity, native item art, channel colors and user-selected War/consumable colors independent. Wynntils-owned UI and ordinary Minecraft menus are outside the theme boundary. Do not recolor the game or invent new feature gates/backend operations.

## 6. Make changes visible without stale styling

- Replace static-final captures of active palette values with runtime palette resolution or surfaces that consult the resolver when drawn. Sharing a `Surface` that captured a color at class load is not enough.
- Rebuild already-created owo labels/components that carry baked colors while keeping settings navigation/search/scroll. Use palette revisions for cached render data that contains colors.
- Active HUD cards, editor overlays and screenshot previews should take the next resolved palette without resetting timers, rosters, selections or animations. A theme change is presentation-only.
- Existing historical vanilla chat messages retain the styles they had when posted; newly created RALLE messages use the active theme. Do not rewrite the chat log or add a chat interception mixin solely for retrospective recoloring. This is a proposed boundary to document with the feature.
- Review GPU wheel snapshots and other cached images: an already-running dissolve can finish with its captured palette, then the next capture uses the new palette. Never regenerate a GPU capture every frame or alter effect completion cues.
- Resource reload and reconnect must retain the saved theme without unwanted backend requests or domain-state resets.

## 7. Validation and documentation

First check all 25 credited labels through the existing dropdown at supported GUI scales: reachability, width, selection and dismissal. Resolve any stock-control layout limitation before completing all styling migrations.

Focused automated checks:

- Catalog integrity and exact source palette values; 25 choices; stable IDs; local persistence; missing/invalid saved values fall back; Default theme's resolved colors match the pre-change runtime tokens.
- Feature defaults remain disabled and keybindings unchanged. Loading/changing themes cannot trigger backend authentication, skin/account requests or party mutations.
- Shared label formatting in the menu and closed selector; exact contributor suffix, corrected IGN and Default theme without a suffix; stable IDs remain independent of formatted labels.
- Palette changes refresh UI/caches without resetting state; fixed gray controls and independent semantic/user colors remain unchanged.
- Extend `RalleChatMessagesTest`, screenshot outline/geometry tests, HUD frame tests and selector renderer tests for non-default palettes and retained click/hover metadata.

Run focused tests first, then the repository's supported test/build checks. In-game checks with and without Wynntils: multiple GUI scales/resolutions, Vanilla and Karla, longest credited labels, all 25 options, menu edges, existing input behavior, Escape/outside click, offline use, setting persistence, representative dark/light/low-contrast themes, all major themed surfaces and unchanged Default theme. Check that existing texture/item rendering remains unchanged.

Update `docs/ARCHITECTURE.md` near settings/presentation with the setting key, palette boundaries, text attribution and message refresh behavior. Update the RALLE UI skill's default-only palette statements to describe Default theme plus shared theme roles, while retaining fixed grays, semantic colors and blur rules. Update AGENTS.md only if a durable project constraint actually changes.

## Planning decisions

No outstanding skin or contributor-display question remains. The approved selector uses the current owo-lib dropdown and `Theme name (IGN)` text, with `Default theme` alone. Placement below Interface Font remains the proposed integration location. Verify stock-dropdown capacity during implementation as described above.

## Palette catalog

Exact submitted RGB values; the default option label is changed only for the mod selector.

| Theme | Contributor IGN | Background | Outline | Accent |
| --- | --- | --- | --- | --- |
| Default theme | None | #041330 | #FFFFFF | #F2B84B |
| Royal Dynasty | SaltyKing | #6A1B9A | #FFFFFF | #F2B84B |
| ??? | SpaseCow | #1A4A00 | #FFFFFF | #FFBABA |
| Housing crisis | SpaseCow | #5E0606 | #065E32 | #001702 |
| Gloopy Cave | SpaseCow | #00D636 | #A100D6 | #D60036 |
| Countx Sini | Nothesinistrtype | #6DDFFF | #FFFDAE | #851A1C |
| Prince's Palette | Robturne | #9B00E2 | #AE0070 | #C10000 |
| Tempest | Robturne | #DBDBDB | #0094FF | #FFD400 |
| Scarecrow | Robturne | #CCDEB6 | #377141 | #000000 |
| Joker | NeonRider | #660C0C | #020202 | #BB8856 |
| Coral Castle | ToolyTom | #FFB5C2 | #FF7B6E | #2F9FBF |
| Tidal Tom | ToolyTom | #2A91A3 | #003888 | #3EAAE6 |
| Forrest Phil | ToolyTom | #3C4D18 | #8C613B | #8A5525 |
| Cartman | ToolyTom | #31AAA9 | #F8E0A4 | #A82020 |
| Sunset Spase | ToolyTom | #FF8958 | #FF5A5E | #FFC75A |
| Grassy Green | ToolyTom | #1D6923 | #95FF9D | #75FF80 |
| Mocha Madness | ToolyTom | #6D3B07 | #926441 | #E3B7A0 |
| Radioactive Robert | ToolyTom | #7AFF00 | #EBFF00 | #EBFF00 |
| Julian Jellyfish | ToolyTom | #A130B2 | #FA53D7 | #B900FF |
| Mad Max | ToolyTom | #F9B138 | #F5852E | #D2672C |
| False King | ToolyTom | #9B9C9D | #2E566A | #90AEBD |
| River Styx | maxkarson | #000000 | #0AF539 | #0AF539 |
| Crimson Planet | maxkarson | #000000 | #FF1100 | #FF1100 |
| Cyclic | maxkarson | #000000 | #00CCFF | #00CCFF |
| Hot Chocolate | _Hotchocolate | #5D4037 | #795548 | #A1887F |
