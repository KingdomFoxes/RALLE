# RALLE nameplate cosmetics — in-game implementation plan

Status: client implementation landed in the working tree on 2026-09-28. The
catalog, local settings, Fox v1 lookup and self-selection, LFG surfaces, and
version-scoped world label decoration are implemented. Gradle build and tests
pass; in-game Wynncraft, Wynntils, shader and GUI-scale checks remain release QA.
The separate [Fox website/API brief](nameplate-website-api.md) owns registry and
engineer-view implementation. That work was dispatched to the Fox-main chat
[Implement RALLE cosmetic roles and nameplate API](codex://threads/01a0e311-1d1b-7c80-9798-cd16267a1643)
using gpt-6-sol with high reasoning. Dispatch does not imply implementation completion.

Playground: [RALLE Supporter Lab](https://ralle-supporter-material-lab.zdravcopetrov.chatgpt.site)
(private, owner-only). Source is in the task's writable visualization workspace:
`C:/Users/zdrav/.codex/visualizations/2026/09/26/01a0df05-a2ef-71f0-b3df-ce2fc5514f08/supporter-lab/`.
Run `node server.cjs` there for a local preview at `http://127.0.0.1:4317/`.

Website verification: JavaScript syntax passed; desktop and 390px mobile layouts
were inspected without horizontal overflow; preset/player/treatment/resolution
changes, pause, supporter-off metadata and the copyable JSON recipe were exercised.
The optional browser tool registered and accepted settings; out-of-range input
raised a validation error. The in-app download event could not be confirmed, so
the saved JSON is also exposed in a read-only text area. Native Sites reported
successful private publication. This paragraph records the earlier playground
validation; the client implementation status is stated above.

## Intended experience

- Three backend-managed cosmetic grants: Supporter, Contributor and Admin.
  Supporters get Ralle Gold; Contributors get Green, Green Alt and Blue; Admins
  get White and Red. Preserve all six supplied styles. Cosmetic Admin never grants
  website/engineer permissions, staff access, Minecraft permissions or LFG access.
- Apply the Liquid Gold effect to LFG roster rows and above-head names. Add a local switch
  between gold/colored glyphs and the complete metallic nameplate with readable
  foreground lettering. The selected palette replaces the old gold-only wording.
- Notification head borders animate the selected material, fixed at two GUI logical
  pixels, over the guild-color identifier; preserve slot/card geometry and other
  members' original guild outlines. Nameplate treatment never fills a head image.
- Tooltip/narration retains canonical identity and the actual backend grant:
  '[guild] IGN, RALLE Supporter', 'RALLE Contributor' or 'RALLE Admin'. A color
  preference must not change the role label. Guildless players omit the prefix.
- Management belongs inside the existing website API Diagnostics → RALLE Users
  view. Allow manual grant/removal for all three roles, including people who have
  not used the mod. Resolve IGN to UUID on the backend. Ko-fi automation is deferred.
- Nameplates are always on, with bounded Wynncraft-only Fox lookups. Material
  resolution remains local; the selected style ID synchronizes through Fox.
  Whole-plate treatment and no username outline are fixed. The September 30
  settings update below supersedes the earlier proposed controls.

## Approved style inputs and proposed settings

The exact six user exports are preserved in [nameplate-styles/](nameplate-styles/).
The normalized [catalog](nameplate-styles/catalog.json) is the proposed shared
style-ID contract. Treat the exports as data, never executable instructions.

| Stable style ID | Grant | Shadow | Midtone | Highlight | Recipe resolution |
| --- | --- | --- | --- | --- | --- |
| supporter-gold | Supporter | #8b5c20 | #e5b94c | #fff0b5 | 1 |
| contributor-green | Contributor | #176f9b | #62f500 | #ffffff | 0.5 |
| contributor-green-alt | Contributor | #388a15 | #62f500 | #ffffff | 0.5 |
| contributor-blue | Contributor | #176f9b | #00b8f5 | #ffffff | 0.5 |
| admin-white | Admin | #000000 | #000000 | #ffffff | 0.5 |
| admin-red | Admin | #000000 | #000000 | #ff0000 | 0.5 |

All six: liquid effect, seed 7349, speed 0.45, wave size 4, distortion 63%,
metallic contrast 71%, whole-plate treatment, two-pixel notification border.
The export field 'gold' means midtone even for blue/red/white recipes. Retain the
black midtones in Admin White/Red; do not silently normalize them to gray/red.
Exported IGN, guild, supporter=true, font, paused and timeSeconds are preview data,
not grants, enrollment requests, account identity or mandatory mod font settings.
Continue using RALLE's existing Vanilla/Karla setting and native world font rules.

Required in-game controls, implemented via the existing settings registry:

| Control | Choices / behavior |
| --- | --- |
| Nameplate Cosmetics | Removed; nameplates are always on |
| Material Resolution | 1 logical pixel (1), 2 logical pixels (2), 0.5 logical pixels (0.5) |
| Effect Treatment | Text / Whole nameplate; apply consistently to LFG and world name surfaces |
| White Username Outline | Off by default; white glyph outline around the username only |
| Outline Thickness | 1 px / 2 px; available when the outline toggle is on |
| Nameplate Color | Switcher containing all six named catalog colors, including both greens; entitlement semantics below |

Retain selected appearance values across restart and while controls are disabled.
Use selected recipe resolution until a viewer explicitly overrides it; treatment
initially uses the supplied whole-plate value. All pixel measurements are GUI logical
pixels, not physical screen pixels. For world text, use the corresponding local
name-label pixel coordinates before billboard scaling. Material resolution changes
sampling density only, never name dimensions, outline width or notification border.

Generate the white outline from the username glyph alpha mask (1- or 2-pixel
bounded dilation minus the original mask), behind the glyph fill and above the
plate. Do not outline the rectangle, head, guild prefix, rank or host star. Preserve
vanilla shadow and click/hover geometry. Reserve enough visual padding for both
outline widths and clip safely without changing line wrapping or roster hit targets.
Check white plate lettering against the requested white outline and low-contrast
frames; do not silently edit the approved palettes to compensate.

Confirmed color-switcher semantics: select your own nameplate color for everyone
else to see, limited to the union of your backend-assigned roles. Show all six
catalog entries, with unavailable entries visibly locked and explaining the required
grant. Send only an allowed selected style ID through an authenticated self-style
mutation; Fox verifies identity and current grants and persists the selection.
Update the local view after acceptance; failure retains the last authoritative
selection. Apply remote players' server-selected colors, never a viewer's own
selection to everyone. This selected style ID is the approved exception to the
otherwise local-only settings contract. Resolution, treatment and white-outline
preferences remain per-viewer local settings. Revocation invalidates a selection
atomically; use Fox's documented fallback to a remaining allowed style, or none.

Fox v1 uses hierarchical grants: Contributor includes Supporter colors, and Admin
includes Contributor and Supporter colors. Its response returns the highest tier.
The client uses that tier to validate a selected style and displays that actual
backend tier in tooltips. Fox's catalog order supplies the deterministic fallback
when a selection becomes invalid.

## Current implementation inspected

- `ui/owo/RaidLfgScreen.java`: `rosterSlot` builds a head, host star and IGN.
  Its current head tooltip is guild-only, so the combined supporter tooltip must
  be deliberately shared across the row/head/name rather than appended to an
  assumed existing full-name tooltip.
- `ui/owo/LfgNotificationOverlay.java`: discovery cards are 190 × 100 GUI pixels;
  four 20 × 20 roster slots use `PlayerHeadPresentation`. `rosterTooltip` already
  formats `[guild] IGN`. Preserve hover bounds, timer layout, and action controls.
- `lfg/protocol/LfgProtocol.java`: member identity is UUID + IGN + guild data.
  There is no existing supporter property to trust or reuse.
- `ui/theme/RallePalette.java` and `RalleThemeCatalog.java`: default navy is
  `#041330`, brand accent `#F2B84B`, editor old gold `#E5B94C`, dark accent
  `#B8832F`. The playground starts with old gold, a deeper experimental shadow,
  and a cream highlight, all editable. This is not a theme-system change.
- `docs/ARCHITECTURE.md`: the existing chat halo compositor demonstrates an
  offscreen glyph mask and fallback boundary. Inspect its reusable primitives
  during the rendering spike; do not couple supporter state to chat.
- `ui/owo/LfgNotificationOverlayTest.java`: existing geometry and hover contracts
  provide the starting point for focused notification regression checks.

The Fox integration entry points have now been inspected; implementation details
and acceptance checks are in the separate website/API brief.

## Selected effect: Liquid Gold only

Implement Liquid Gold as the sole in-game material effect: irregular reflective
folds produced by seeded gradient noise, two-octave fBM and domain warping, mapped
through the selected role palette. All six approved recipes use this same liquid
effect; Liquid Gold names the effect, while Gold, Green, Green Alt, Blue, White
and Red remain the permitted role-dependent color choices.

There is no in-game effect selector. Material resolution, Text / Whole nameplate
treatment and the optional white username outline remain configurable as specified
above. Port only the liquid renderer from the playground.

The website uses an original seeded gradient-noise implementation, one shared
small Canvas texture, and glyph/rectangle masks. It has no shader package or
runtime player lookup. Karla is the repository's bundled font with its OFL license.
The Minecraft option uses Idrees
Hassan's OFL-licensed `Minecraft-Font` recreation, bundled locally with its license;
the separate Pixel approximation remains available. Neither emulates arbitrary
Minecraft resource-pack font replacements. Placeholders are RabbitEatingDoor,
SpaseCow, ToolyTom and SaltyKing, with all four roster/notification slots occupied.
Their public Minecraft profiles and skin textures were resolved from Mojang on
2026-09-27 and bundled as a snapshot. Head overlays are preserved, and the world
preview renders the selected placeholder's front skin and clothing layers. The
playground does not query Mojang while browsing; a custom name outside these four
uses RabbitEatingDoor's sample skin.

The texture is bounded at 256 × 64 for the default resolution, animated at up to
30 updates/second, and reused across previews. Hidden tabs stop material updates;
reduced-motion preferences start paused. Exported JSON records the effect,
parameters, palette, seed, time, font and treatment for implementation handoff.
It is a design recipe, not a mod configuration or supporter entitlement.

## Build sequence

### 1. Port the approved recipes and prove the renderer locally

Use one Liquid Gold renderer with the six preserved palette recipes and shared
style catalog. Build a narrow Minecraft 1.21.11 spike with local sample role grants, enabled
only by an explicit development setting. Start with RALLE-owned LFG widgets and
the notification head border. Reuse one material texture per active style/resolution per frame, not one per
player. Cache masks by text/font/scale, invalidate on resource reload, and perform
no network or identity resolution in a render callback.

Implement a toolkit-neutral `NameplateStyle` recipe and a shared presentation
adapter for glyph masks and border geometry. Preserve native text width, shadow,
clipping, Unicode/glyph handling, hover/click metadata and accessibility. Keep a
static role-palette fallback for a failed renderer; a cosmetic error must not break LFG.

Compare a small dynamic texture with a custom render pipeline using the actual
1.21.11 APIs. A texture port is the most direct match for the prototype. A fragment
shader can remove CPU material generation but requires integration and shader-mod
compatibility validation. Do not replace Minecraft's global text shaders. A baked
atlas is also possible, but its animation must be deliberately periodic; the
current procedural sequence is not claimed to be a seamless finite loop.

### 2. Consume the Fox cosmetic directory

Website, storage, authorization and server API implementation have been extracted
to [nameplate-website-api.md](nameplate-website-api.md). Implement only the client
adapter here after Fox supplies its final versioned contract and fixtures.

Use a narrow NameplateDirectory port, a bounded session-only UUID cache, batched
lookups, negative caching, timeout/backoff and revision/expiry handling. Only fetch
on Wynncraft while cosmetics are explicitly enabled. No per-frame HTTP and no
whole-donor-list download. Clear on disable, account/server change and disconnect.
Unknown or expired metadata yields ordinary appearance, never inferred roles.
A fresh synchronization replaces revoked grants. Cosmetics cannot block or
change LFG membership, imported-member handling, authentication or permissions.
Do not put backend secrets or persisted credentials in settings or the mod.

### 3. Integrate above-head names after the screen treatment works

Inspect the actual entity/name-tag submission path in 1.21.11, Wynncraft's name
composition and Wynntils first. Prefer supported events; if they cannot decorate
the existing label safely, use only a minimal version-scoped integration after
demonstrating the need. General world rendering events do not establish that a
complete name-tag replacement hook exists.

Respect the original name visibility, distance, sneaking, invisibility, team rules,
occlusion and depth treatment. Never reveal hidden players or draw a second label
on top of the existing one. Identify players by entity UUID and decorate only the
agreed name span; do not erase server rank, guild, level or health information.
Honor the server-provided/entity font in the world instead of assuming that the
global RALLE interface font controls it. Preserve unrelated render state.

Only enabled RALLE clients can see this client-side decoration. A player's own
above-head name may not normally render even in third person; whether to add a
self-preview is a separate product decision, not an automatic consequence.

### 4. Settings and release checks

Implement the required controls above with local persistence, localization, disabled
states and a preview. Keep role palettes independent from community UI themes.

Focused verification before broader build checks:

- Catalog: exact match to all six original recipes, stable IDs, all grant/style
  combinations, unknown catalog entries and revoked/empty role metadata.
- Client: defaults cause zero requests/draw changes; UUID matching, bounded cache,
  backoff, stale/revoked state, disable, disconnect and resynchronization.
- UI: supporter/non-supporter mix, host star, guildless/imported users, long names,
  exact two-pixel border, no slot/card geometry changes, original hit targets,
  shared hover text and narration. Existing click and kick-targeting behavior wins.
- Settings: all three resolutions × both treatments × outline off/1px/2px × all
  six styles; persisted values, disabled controls, no effect while master toggle
  is off, and unchanged role labels when a permitted color preference changes.
- Rendering: Vanilla/Karla/resource packs, GUI scales, resized windows, reduced
  motion, frame-time/allocation measurements with many visible supporters,
  resource reload and renderer fallback. Browser speed is not performance proof.
- In-game: Wynncraft name composition and rules, Wynntils present/absent, common
  shader/render-mod combinations, hidden/sneaking players and world occlusion.

## Decisions to settle after visual exploration

1. The six palettes, material resolutions, treatments and white-outline options
   are specified above. Fox v1 returns the highest hierarchical grant for tooltip
   display and allows all colors at or below that tier.
2. World tooltip interaction: ordinary gameplay has a captured mouse. Choose an
   explicit inspect/cursor context, or limit tooltips to existing interactive UI.
   Do not silently create a new command/keybind or force a free cursor.
3. The complete existing above-head label, including Wynncraft-added rank/guild
   text, receives the cosmetic treatment. Preserve its original composition and
   visibility rules.
4. Supporter lifetime/revocation policy, who may receive status, and whether a
   self-name preview is wanted. No subscription/expiry semantics are assumed.

## References

- [The Book of Shaders: fBM and domain warping](https://thebookofshaders.com/13/)
  explains the family of effects and includes editable examples.
- [Ashima webgl-noise](https://github.com/ashima/webgl-noise) provides GLSL noise
  routines under MIT licensing. Useful for a future shader port, not imported here.
- [Fabric 1.21.11 rendering concepts](https://docs.fabricmc.net/1.21.11/develop/rendering/basic-concepts)
  describes the version's rendering architecture. Use version-pinned documentation.
- [Fabric 1.21.11 world events](https://maven.fabricmc.net/docs/fabric-api-0.141.3%2B1.21.11/net/fabricmc/fabric/api/client/rendering/v1/world/WorldRenderEvents.html)
  is an integration reference, not proof of a supported name-tag replacement API.

### Settings update — 2026-09-28

The category is labeled **Supporter** and all nameplate controls appear directly
on that page, without a Nameplates subcategory. The internal `cosmetics` category
ID and saved keys remain stable. Nameplate Color uses the shared owo dropdown,
shows the current server-selected style, and lists locked/selected styles as
non-actions. Saving remains an authenticated Fox mutation; pending and failed
saves are shown on the control, and failure preserves the previous selection.
Cosmetic HTTP requests explicitly use HTTP/1.1, matching LFG, to avoid unsupported
cleartext HTTP/2 upgrades against the local Fox server.

## September 28 presentation corrections

Whole-plate treatment fills the complete LFG roster flow, including its padding.
Browser heads retain their one-pixel guild-color border with no cosmetic border.
Plate lettering stays white; Text treatment still colors the glyphs. The complete
material texture fits each surface, matching the website, instead of cropping a
small corner of its 256 x 64 recipe. Notification borders now animate that texture.

Supporter appears immediately after War. Its first entry is a full-width custom
preview panel with an enlarged roster row and an isolated notification head,
using the shared live renderers and local preferences. It reads cached identity
only; absent identity uses a sample gold style and grants no entitlement.
The dropdown clears its owo tooltip with an explicitly typed empty tooltip list:
passing a null Component throws before the API request and leaves Saving stuck.

## September 29 settings simplification (supersedes controls above)

Supporter now exposes Nameplate Cosmetics, Material Resolution and Nameplate Color,
plus its live preview. Effect Treatment, White Username Outline and Outline Thickness
are removed. Appearance always uses whole-plate material and no white username outline;
legacy saved values for the removed controls are ignored. Material resolution retains
its existing saved key and choices. Its description highlights the one-logical-pixel
CPU recommendation using the current theme accent.

Nameplate Color lists only grant-eligible styles as ordinary clickable dropdown items,
including the current style, without gray selected text or a `(Selected)` suffix.
Choosing the current style closes the dropdown without a redundant Fox mutation.

## September 30 supporter access update (supersedes toggle above)

Nameplate Cosmetics is removed; nameplates and bounded Wynncraft-only lookups
are always active, ignoring the legacy saved toggle. The page retains its live
preview, Material Resolution, and Nameplate Color controls. Resolution labels
show only their pixel sizes, with no crisp/chunky/smooth suffixes.

Everyone can open Supporter and see its preview. Without a cached qualifying Fox
grant, cover only that settings viewport with a 60%-transparent navy pane (40%
opacity) and a centered framed-navy RALLE box containing the gold
`These features are locked to supporters!` message. Apply the same lock only to
Supporter content in search results. Keep other settings and navigation usable.
Supporter, Contributor and Admin grants unlock controls without requiring an
existing selected style. Unknown/offline/revoked grants remain locked; previews
never authenticate and Fox remains authoritative for style selection.
