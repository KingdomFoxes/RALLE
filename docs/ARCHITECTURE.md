# Client architecture

RALLE remains a single client-only Fabric mod, but its code is split by
responsibility so chat compatibility failures cannot take down Raid LFG and UI
choices do not leak into product logic.

## Dependency direction

`RalleClient` is the composition root. It creates the registries and adapters,
then seals registration. Feature code may depend on RALLE APIs and narrow
platform ports. It must not depend on a concrete settings screen.

- `api.feature`: feature lifecycle and registration contracts.
- `api.settings`: UI-independent categories and entries owned by RALLE.
- `api.hud`: normalized, resolution-independent HUD element placement and local
  persistence owned by RALLE.
- `ui.owo`: the owo-lib adapter that renders the settings registry.
- `chat`: Minecraft chat integration and Wynntils compatibility boundary.
  Narrow graphics transforms implement message direction,
  horizontal alignment, and four shadow styles while retaining vanilla chat
  scale, opacity, spacing, timing, scrolling, and interaction metadata. The
  wrapped Full shadow collects every visible line into one fixed-resolution 2x
  offscreen glyph mask, composites the sixteen half-pixel halo samples in one
  GPU quad, and then draws the original text once. Its mask removes interaction
  metadata while retaining glyph styling, and clickable-text capture bypasses
  visual collection entirely. Target, projection, and output overrides are
  restored after preparation. If the compositor becomes unavailable, a
  session-stable batched glyph fallback preserves the visual effect without
  affecting other chat modes or Raid LFG. Local chat customization is available
  in singleplayer and on any multiplayer server. The disabled-by-default
  Persistent Chat option raises the in-memory logical-message and wrapped-line
  ceilings and preserves displayed `GuiMessage` values across transition-driven
  chat clears. Vanilla still flushes pending chat and clears delayed deletions,
  while draft and command-entry history handling remains entirely vanilla. The
  retained components, signatures, tags, and interaction metadata are never
  persisted or reconstructed from logs; manual F3+D clearing remains unchanged.
  The disabled-by-default `chat.chat-timestamps` projection records each logical
  message's local receive time in session-only identity metadata even while its
  presentation is disabled. When enabled, it renders `[HH:mm:ss]` flush with the
  chat's left edge, reserves the timestamp and separator width before wrapping,
  and composes the styled prefix onto every rendered line,
  so transformations and transparent chat screenshots include timestamps without
  changing source messages, signatures, tags, logging, or interaction metadata.
- `sound`: client-only registered UI sound events and playback adapters. LFG
  playback and its notification-setting check run on the Minecraft client executor:
  REST/live-event store observers may run on worker threads and must never mutate
  SoundEngine's active-sound maps directly. Chat
  selection injects this narrow port, while its Minecraft implementation owns
  parent-setting gates, count-to-cue mapping, rate limiting, and coalescing.
  Raid LFG discovery cards own their lifecycle cues. The shared join-result
  presentation plays the vanilla respawn-anchor charge variants after a
  confirmed viewer join from either the main screen or a HUD card. The main
  screen plays the first amethyst resonance after confirmed party creation.
  An explicit successful Leave or Disband from the main screen, notification
  card, or keybind Action Bar path plays the deplete variants. Visible discovery
  cards play occupied-slot cues as their observed rosters grow. An always-on
  synchronized-state observer owns the same cue for later members joining the
  viewer's lobby, even when the browser is closed or no HUD card is present.
  Reconnect snapshots remain silent. Cancelling a join countdown is silent.
  Chat Selection Sounds reveals a local Instrument choice, persisted as
  `chat.chat-selection-instrument`: `xylophone` (default), `acoustic-guitar`,
  `bass-guitar`, `piano`, or `drums`. Missing or invalid saved choices fall back
  to xylophone. Both sound and screenshot toggles gate playback and visibility.
  Melodic banks cap at ten D-major notes in instrument-specific registers:
  xylophone D5 through F-sharp 6, acoustic guitar D3 through F-sharp 4,
  bass D2 through F-sharp 3, and piano D4 through F-sharp 5.
  Copy success plays the root then its octave 80 ms later.
  Drums map counts 1 through 5+ to bass drum, floor tom, low tom, high tom, and
  snare; successful copying plays one crash. All banks share the 40 ms rate
  limit and latest-count coalescing; cancellation/failure remain silent.
  Packaged chat sounds are original procedural synthesis. The offline generator
  is `tools/generate_chat_instruments.py`; provenance accompanies the assets
  in `licenses/ralle-sounds/README.txt`. The guitar uses a plucked-string model;
  bass has stronger upper harmonics and level, while drums retain audible
  stick/beater transients and membrane resonance. Generator `--previews` writes
  audition WAVs to `build/audio-previews` without per-preview normalization.
  Playback performs no network activity.
- `lfg`: strict Fox protocol, authentication, live connection, immutable lobby
  projection, and Raid LFG orchestration. It remains inert until enabled and
  connected to Wynncraft.
- `war.consumables`: local, immutable ordered displayed-name rules and the
  container-slot highlight service. The service is inert unless its setting is
  enabled, a play connection exists, and the host is a real Wynncraft domain.
- `war.hqdistance`: an optional version-gated Wynntils adapter around immutable
  territory snapshots, a pure bidirectional graph projection, provisional queue
  estimation, cached inspection state, and collision-aware rendering. It consumes
  only data Wynntils already maintains and performs no polling or backend calls.
- `war.queue`: bounded, memory-only sender attribution projected from verified
  guild-chat envelopes onto Wynntils' existing Guild Attack Timer render tasks.
  Pure identity, parsing, tracking, and formatting code is separated from the
  optional version-gated event/model adapter and HUD-scoped mixin.
- `platform`: Fabric/Minecraft adapters such as commands, keybinds, connection
  lifecycle, local persistence, and future clickable chat notifications.

## Foundation invariants

- Every feature defaults off on a new install, and every keybind defaults to
  Unbound. Persisted settings remain authoritative for existing installs.
- Initialization performs no network requests and changes no game behavior.
- owo-lib is contained behind `SettingsScreenFactory`; future settings register
  through `SettingsRegistry` rather than constructing owo components directly.
- Feature IDs and setting/category IDs are validated and unique.
- Registries are sealed after bootstrap to catch accidental late mutation.
- Minecraft-specific hooks should use supported APIs before mixins.

## Consumable highlights

`war.consumable-highlights-enabled` defaults to false and is persisted in
`config/ralle.properties`. The ordered rule document is independent of that
toggle and lives in `config/ralle-consumable-highlights.json` with
`schemaVersion: 1`. Missing files are seeded with the nine WynnColour semantic
groups; the original primary name is not repeated as an alias. Rule mutations
validate the complete next list, write a sibling temporary file, replace the
document atomically when supported, and only then publish an immutable snapshot.
Normalized primary words and aliases must be unique across the whole document.

Before matching a name, the highlighter requires one of Wynncraft's exact
styled tooltip-emblem markers for Potion, Food, or Scroll. It reads only that
structured marker from the lore component; it never name-matches lore text or
uses the vanilla backing item as proof of type. Matching then uses only
`ItemStack#getHoverName().getString()`. It performs Unicode-aware case folding,
turns punctuation and formatting separators into spaces, collapses whitespace,
and compares complete words or phrases. It does not infer singular/plural
variants. The first rule in stored order wins, and a bounded displayed-name
cache is cleared whenever a new snapshot is published. Cache keys retain the raw
displayed name so repeat slot renders skip normalization as well as rule matching.
Matching and snapshot updates share one lock to prevent stale cache entries after
an edit; empty rule lists bypass matching entirely.

An optional tail injection into `AbstractContainerScreen#renderSlot` draws a
single full-opacity, one-pixel border outside the 18-by-18 slot after its item
and decorations. This covers vanilla container screens and compatible derived
Wynntils screens without special overlay arbitration. Solid rules use stored
RGB; rainbow rules retain that RGB while rendering the fixed 0.0004-speed,
0.85-saturation positional perimeter animation.

The Consumables editor is hidden while highlighting is disabled and appears
immediately below the master toggle when highlighting is enabled.
As a toolkit-neutral custom settings panel, it participates in global settings
search through its registry metadata and renders the complete editor in matching
results. Unmet dependencies still hide custom panels; future special settings
containers inherit the same search behavior without screen-specific handling.
Import reads at most 1 MiB and transactionally appends a complete versioned
document; malformed, unsupported, internally duplicate, or conflicting imports
change nothing. Export writes the complete list, including a valid empty list,
and asks before overwriting. Both use native dialogs with blocking dialog and
file work off the render thread. Reset replaces only the rule list and never
changes the master toggle.

The color dialog's scroll, potion, and food slot preview resolves custom-model-data
selectors and models from the active Wynncraft resource pack. RALLE does not ship
copies of those textures; missing or incompatible pack entries fall back to
recognizable vanilla items without affecting highlight configuration.

## HQ distance and queue estimate

`war.hq-distance-enabled` defaults to false and persists in
`config/ralle.properties`. It is visible but unavailable unless the installed
Wynntils version is 4.2.7 or newer for Minecraft 1.21.11. The compile-only
development dependency is pinned to Modrinth project `dU5Gb9Ab`, Fabric version
artifact `jeBTZ3Zn`; Wynntils is never bundled.

The optional `TerritoryPoi#renderAt` tail hook is restricted at runtime to
`GuildMapScreen`. It draws only for the hovered territory while the
configured inspection key is down (either Ctrl by default), using Wynntils' transformed center, zoom scale, and active map
scissor. It does not intercept input. A known active attack timer suppresses both
RALLE labels so Wynntils' real timer remains authoritative. Labels remain anchored
above and below the centered guild tag or HQ crown without collision or territory
fit checks, including when zoomed out. Overlap with names and neighboring
territories is intentional; the map viewport scissor still applies. On another
guild's HQ, the queue estimate sits below its crown, just as ordinary territories
place it below their guild tag. Labels use Wynntils' own font renderer and
four-direction outline at native size with
pixel-aligned origins; they never shrink to fractional scales to fit a territory.
Unexpected adapter or linkage failures are logged once and disable only this
overlay for the remainder of the client session.

`WynntilsTerritorySnapshotSource` projects the current guild identity, all known
territory-profile endpoints, and advancement-backed links without retaining
mutable Wynntils objects. Links are normalized as bidirectional; a territory
without advancement details can still use reciprocal links reported by neighbors.
`TerritoryRouteCalculator` performs breadth-first search from the single owned HQ
through territories regardless of owner or disagreement between ownership sources.
Ownership reliability determines HQ identification and the guild-holding color
scale, not physical connectivity. This prevents newly captured foreign territories
from blocking shortcuts and inflating both distance and provisional duration.
Owned destinations use the same hypothetical distance result. A complete ownership
projection showing that the player's guild owns no territories renders red
`No Hq!` without a duration. Missing identity, an incomplete ownership projection,
missing or multiple owned HQs while holdings remain, unreliable HQ ownership, or
globally unavailable input yields `Unknown`. A destination unreachable through known links also yields
`Unknown`; HQ yields `0` without a duration. Links absent from all available
advancement data cannot be inferred from geographic adjacency.

`HqInspectionService` invalidates its route cache whenever the complete immutable
snapshot changes, covering guild identity, HQ, ownership, and connections, and
is explicitly cleared on disconnect. Non-HQ connected destinations currently
use the historical provisional estimate `60 + 60 × connections` seconds and
render it as `🕒 m:ss`. All distances are calculated in one BFS per changed
snapshot. The connection number uses piecewise linear RGB interpolation from
`#55FF55` at HQ through `#FFFF55` at 60% of the red threshold to `#FF5555`
at and beyond that threshold. The threshold is `max(1, round(1.25 × sqrt(N)))`,
where N counts reliable territories owned by the player's guild, including
disconnected holdings. Foreign and uncertain ownership do not contribute.
For 27, 60, 100, and 180 holdings, red starts at 6, 10, 13, and 17 connections.
Ownership changes recalculate colors without changing the distance or duration
formula; zoom and the furthest territory on the world map do not affect the scale. Unknown labels and
durations remain white. This formula excludes border penalties, taxes, routing
detours, and attack-eligibility cooldowns. Release remains blocked until the
formula is compared with current in-game attack previews and its fixtures are
updated if necessary. Wynncraft's 1.20.4 Hotfix #3 documented a temporary,
gradually increasing timer for repeated free wars, but neither its increment nor
decay window is public and Wynntils' territory snapshot exposes no authoritative
penalty state. RALLE therefore does not fabricate that server-side value.

## War queue attribution

`war.queue-attribution-enabled` defaults to false and persists in
`config/ralle.properties`. It is visible but unavailable unless Wynntils is
4.2.7 or newer for Minecraft 1.21.11, using the same compile-only artifact and
compatibility detector as HQ Distance. Enabling it never changes Wynntils'
configuration: the player must separately enable Wynntils' Guild Attack Timer
overlay, and a sender must supply the guild defense announcement for attribution
to be observable.

While enabled in an active Wynncraft world, a lazily registered Wynntils Match
listener validates the complete current guild indicator, rank pill, speaker, and
body envelope before accepting `{territory} defense is {level}`. All six pinned
defense values are accepted, and the territory must equal a canonical Wynntils
territory name after narrow whitespace normalization. Before matching the body,
the parser replaces Wynncraft's exact newline-plus-continuation-glyph prefix
(`U+CFFFC U+E001 U+D0006` followed by a space) with a space. This accepts
server-wrapped territory names and defense levels while rejecting other line
breaks and appended text. Character nicknames
resolve through hover metadata applying to the speaker span; direct names must be
valid Minecraft IGNs. The observer never cancels or edits chat and performs no
commands, polling, HTTP requests, or backend work.

Canonical territory names are read from the current Wynntils models only while
resolving incoming guild announcements or capture messages. Client ticks reconcile
active timers and session identity without rebuilding the full territory-name set.

Announcements may wait up to ten seconds for the matching timer model entry.
First observed sender wins for one territory countdown; duplicates are idempotent
and conflicts do not overwrite it. A bounded memory cache preserves attribution
through world/server switches, character selection, reconnects, and temporary empty
timer lists. Returning timers must match the territory and cached end time within
ten seconds; clearly different countdowns cannot inherit the old sender. Cached
entries expire at their timer end and are removed on verified capture, setting
disable, integration failure, or an observed account/guild change. Missing guild
data during loading pauses attribution rather than clearing the known scope.
Pending, active, and cached metadata are each capped at 512 territories. No
component, chat history, or completed attribution is persisted to disk. A
cancellation and requeue with an indistinguishable end time cannot be proven to be
a new generation. Manual verification should cover switching worlds with multiple
named timers, returning after expiry, captures during timer reload, and changing
accounts or guilds.

An optional version-gated mixin decorates only the row task returned by
`TerritoryAttackTimerOverlay#lambda$render$0` and its separate editor preview.
It prepends the local IGN in the configured color (Minecraft blue by default) or another resolved IGN in gray,
then a gray ` → `, and otherwise uses localized gray `Unknown`. The original
styled timer component and `TextRenderSetting` are retained, so Wynntils continues
to own defense colors, current-territory emphasis, sorting, font, shadow,
alignment, wrapping, dimensions, and position. Missing, unsupported, disabled,
or failed integration states leave the original task untouched; adapter failures
clear state, unregister the listener, and disable only this feature for the
remainder of that connection session.

An automatic Wynntils defense announcement and an identical manually typed guild
message have the same observable client representation and cannot be
distinguished. Missing announcements, sender-side announcement disablement, and
messages sent before the listener joined are therefore shown as `Unknown` rather
than inferred.

## Local settings

`/ralle settings` opens the owo-lib adapter over RALLE-owned category and setting
models. Values are stored in `config/ralle.properties`; invalid or obsolete
values fall back to their declared defaults. Chat has no global enable setting;
each feature toggle independently gates its behavior, and choice controls depend
only on their paired feature toggle. Chat settings are consumed by the local
chat integration. The Raid LFG toggle gates the persistent Fox client service.
On new installs, all LFG shortcuts default to Unbound; persisted RALLE and vanilla key changes
continue to override defaults. Initial two-way keybind
reconciliation waits until the first client tick so Minecraft's temporary pre-`options.txt`
`UNKNOWN` mappings cannot overwrite persisted RALLE bindings; later changes and manual unbinding
from either settings surface remain synchronized.

Guild rank presentation is stored as `chat.guild-rank-style` with `titles` as
the unchanged default, plus opt-in `stars` and `stars-and-titles` choices. The
star count is decoded locally from the six standard Wynncraft guild ranks and
does not enable the Fox rank gateway. Recruit has no rank pill in stars-only
mode. The combined mode constructs one Wynncraft-font pill: blank cyan filler
glyphs cover compact stars from RALLE's namespaced bitmap font while the
existing pill alphabet renders the public title or, when
`chat.internal-guild-ranks` is enabled and resolves the speaker, the internal
Fox title. Internal rank refreshes retain their existing Wynncraft-only,
opt-in network lifecycle. While either rank-presentation path is active,
hovering any retained rank pill shows the uppercase Wynncraft guild rank,
followed by the resolved Fox title when available. Gendered or neutral variants
use the complete canonical group label, such as `Lord/Lady/Liege`, instead of
only the member's selected form. The member's selected Fox title stays white;
alternative titles and slash separators are gray. The default titles style with Internal Guild
Ranks disabled remains inert.

The one-time installation message uses the shared local RALLE chat presentation
and stores `message-sent=0/1` in `config/ralle-onboarding.properties`. A missing
marker starts at `0` only when no earlier RALLE config or vanilla RALLE keybind
entry is present; existing installs are migrated to `1`. Delivery sets it to
`1`.

Persistent Chat is stored only as the opt-in `chat.persistent-chat-enabled` and
the selected `chat.persistent-chat-limit`. The displayed history itself remains
session-only memory. Enabling starts from messages still held by Minecraft;
disabling immediately prunes the logical and wrapped histories back to the
vanilla 100-entry ceiling and restores normal transition clearing.

Chat Timestamps is persisted only as `chat.chat-timestamps`. Receive-time
metadata is never written or transmitted; it follows retained logical messages
through rescaling, settings refreshes, Persistent Chat transitions, and
deletion-marker replacement, then clears or prunes with the corresponding
in-memory chat history.

OW-like Chat Tabbing is gated only by the opt-in `chat.chat-type-tabbing` setting.
Incoming DM observation uses Fabric `ALLOW_GAME`, ignores action-bar messages,
and requires Wynncraft plus the enabled setting. It verifies the private-message
indicator/color and the local recipient, resolving nickname hover metadata on
the name spans. It does not select DM when another channel is selected. An empty
open DM channel refreshes to the latest contact; a non-empty draft keeps its
destination. Unrecognized envelopes are ignored; no chat is cancelled or resent.
Its latest valid outgoing `/msg` recipient or incoming Wynncraft DM sender and
last selected stable chat type are connection-local memory only.
A new empty ChatScreen restores that type without
overriding command-key input or drafts, while disconnecting clears both values.
The Fabric outgoing-command observer does not mutate or resend commands. The
ChatScreen integration cycles `[Guild]`, `[Party]`, `[latest DM username]`,
and `[All]`, consuming only an unmodified Tab on an empty message body.
The channel label is rendered beside the EditBox, outside its editable contents,
and included in its narration label. Deletion, selection, copy, and cut operate
only on the body. Sending prepends `/g `, `/p `, or `/msg username ` to the body
before vanilla normalization, history, and dispatch; empty bodies send nothing,
and explicit slash commands bypass the selected channel. Typed/pasted supported
prefixes, command history, and vanilla saved drafts recover their channel.
History navigation restores the draft's channel when returning to the current
input. Resizing preserves the channel. Drafts and normal commands retain
Minecraft's Tab completion. The Show Who Queued color action uses the shared
palette icon followed by `Click to edit color` in the existing control lane.

`WynncraftChatInputController` gates passive text-entry detection on Wynncraft,
a local player, and OW-like Chat Tabbing. `WynncraftInputPrompts` normalizes bounded
server text, including wrapped lines and supplementary spacing glyphs. It accepts
market search/quantity/price prompts and anchored instructions to enter names,
amounts, prices, or searches in chat. Player-message prefixes and slash-command
instructions do not match. `ChatInputRequestTracker` correlates input-menu clicks
with a matching server close within five seconds. Add Ally in a Diplomacy menu
and Recruit a Friend use screenshot-confirmed labels; pet rename/name controls
and explicit chat-input tooltip instructions provide additional detection.
Player-inventory slots, non-pickup clicks, navigation, mismatched/expired closes,
manual closes, menu replacement, and disabled/off-server behavior do not switch.

Narrow Minecraft mixins observe the menu click before mutation, system chat before
`ChatListener.handleSystemMessage`, and server versus local menu closes separately.
Fabric chat callbacks alone miss prompts hidden by Wynntils' enclosing listener
wrapper. No Wynntils classes are required and no packet, message, or click is
cancelled or generated. A monotonically changing local revision updates an open
ChatScreen's label, layout, and narration before input/rendering. Saved channel
drafts retain their body while selecting All. Slash commands remain commands.
All is a one-time selection, not a forced input mode: normal Tab cycling remains
available, and All stays selected after submission/cancellation to keep retries
safe. No automatic restoration guesses server completion. Pending menu evidence
clears on new actions, sent chat, timeout, menu replacement, manual close,
feature disable, and disconnect. No new setting, keybind, or saved data is added.

Custom HUD placements are stored separately in
`config/ralle-hud-layout.properties`. Resizable elements such as the v1 chat
box persist normalized coordinates and dimensions. Fixed elements such as Raid
LFG notification cards persist a left/right side anchor and normalized vertical
position; their logical size is not configurable. Removing the chat placement
restores the live vanilla position and dimensions, while removing the
notification placement restores its eight-pixel bottom-right default.

`/ralle hud` opens the layout editor in its all-elements mode. The chat box is
always available there, while Raid LFG notification placement is included only
when Raid LFG is currently enabled. Setting-specific editor actions remain
scoped to their own element; their `Show all` option is preview-only. The fixed
Raid LFG Action Bar may be previewed but is never movable.

## Programmatic UI development

RALLE's settings, chat-layout editor, and Raid LFG screens build their owo
component trees directly in Java. RALLE does not ship XML UI models or use
owo's UI-model hot-reload workflow. Shared surfaces, button renderers, fonts,
and other presentation primitives remain RALLE-owned so screen construction
does not leak into settings, chat, or LFG domain logic.

This is an intentional architecture decision: UI changes use the normal Java
compile-and-run development loop. New screens should compose shared RALLE
presentation primitives and keep dynamic state and interactions separate from
the component-tree construction where practical.

The eventual Fox FastAPI service is a separate repository and remains the sole
authority for LFG state. No backend implementation belongs in this client.

## Raid LFG client protocol

`RaidLfgService` is a persistent client service owned by `RalleContext`; opening or closing the
browser does not own authentication or live synchronization. Networking starts only when the
`raid-lfg-enabled` setting is true and the active server hostname is `wynncraft.com`,
`wynncraft.net`, or one of their subdomains. Both domains are required because the normal
`play.wynncraft.net` entry connection may transfer the client to a regional `.com` host.
Disconnecting, disabling the setting, or changing servers closes the WebSocket and clears the
in-memory bearer credential and lobby projection.
Opening an ordinary Minecraft screen, including Wynncraft's AFK blackout, does not change that
connection context and has no LFG lifecycle effect. A transient WebSocket failure enters the
read-only reconnect flow without clearing the last projection or issuing a party command. The Fox
backend starts its 120-second presence grace only after the player's final authenticated socket is
lost; reconnecting cancels that grace, while expiry closes only the synchronized LFG lobby.

The packaged protocol-v1 base URL is fixed to
`https://kingdomfoxes.com/api/ralle/v1`. There is no player-facing setting or automatic fallback.
The Gradle development command `./gradlew runClient -PlocalBackend` supplies the fixed, process-only
development override `http://127.0.0.1:8001/api/ralle/v1`; ordinary runs and packaged builds remain
on production. Explicitly constructed development and test gateways may use insecure HTTP and
WebSocket transports only for loopback hosts. The JDK gateway is pinned to HTTP/1.1 so local
requests do not attempt an `h2c` upgrade that a local HTTP server may not support.

Authentication uses `POST /auth/challenge`, Minecraft's session `joinServer` proof, then
`POST /auth/complete`. The issued bearer credential is never persisted. `GET /lobbies` provides a
complete authorized snapshot; create, join, leave, disband, kick, lock/unlock, and ping use
`POST /lobbies...` with a fresh
UUID `Idempotency-Key` per player action. A failed transport attempt is retried once with the same
key. The `WS /live` connection sends the bearer credential in its `Authorization` header and must
deliver a complete `snapshot` frame before any `lobby.upsert` or `lobby.remove` frame.

`session.expiring` starts one parallel credential renewal while the current credential and live
projection remain usable. Repeated expiry frames do not start additional status, challenge, or
completion requests. The replacement WebSocket must deliver its fresh snapshot before the service
atomically swaps connections; the old socket closes only after that swap. Renewal retries use the
same capped exponential schedule as reconnects and never run sooner than `Retry-After`. If the old
credential expires first, mutations become read-only until a replacement snapshot is accepted.

If a mutation exhausts its one same-key transport retry without a response, its idempotency key and
pending interaction remain in memory while the client reconnects. A fresh snapshot reconciles
create, join, leave, lock/unlock, and disband from authoritative state without resubmitting the
mutation. Kick and ping remain explicitly unknown when the snapshot cannot prove their outcome.
Bounded party kick and disband commands run at most once, only after a REST acceptance or a safely
reconciled disband. A live frame arriving before its matching REST response remains valid; the
duplicate global revision is ignored by the store.

The status response contains only the feature flag and protocol version. Authentication identifies
the client protocol but does not send or compare the mod build version. Unsupported protocol
versions enter an incompatible client state without a release link; exact mod-version enforcement
is deferred.

Kick, lock/unlock, and ping remain Fox-authoritative host actions. An accepted kick removes the
member, applies a 120-second rejoin block, and causes exactly one bounded `/party kick <IGN>`
command on the host client. Recipient-scoped `party.ping` and Discord-originated
`party.kick-command` frames are ephemeral, are never replayed after reconnect, and do not advance
the immutable lobby projection. Ping is limited to once per 30 seconds per lobby and is delivered
to current members other than the host; linked Discord members are notified through the same
FastAPI-owned outbox flow.

An explicit host disband sends exactly one bounded `/pa disband` command only after the backend
accepts the synchronized lobby disband. Failed disband mutations and backend-initiated lobby
closures never disband the Wynncraft party.

The host invitation controller watches only accepted `LIVE` store changes and offers invitations
when the local host's lobby genuinely transitions from below capacity to full. Initial and
reconnect snapshots, refreshes, local mutations, stale revisions, and non-host lobbies never create
an offer; a later below-capacity to full refill does. If that lobby already has a persistent HUD
card, it receives the affirmative `Party filled` action. Otherwise the controller posts one local
RALLE chat notification with opaque client-only actions for inviting all or a synchronized member.
Those actions are intercepted in the existing chat click path and never register, expose, or send
another `/ralle` command.

All accepted invitation actions re-resolve the current authoritative host lobby and member names.
One controller-owned queue holds at most three distinct pending targets and emits `/pa <IGN>` no
faster than once every 600 milliseconds. A target becomes eligible for another explicit invitation
after its queued command executes. Disconnect, lobby removal, or lost host authority cancels
pending work, and a member who authoritatively leaves is skipped. This automation is local,
session-only, and remains inert unless Raid LFG is enabled, online, and freshly synchronized; it
adds no protocol message, backend state, setting, sound, or unbounded command loop.

Protocol JSON is decoded explicitly. Missing fields, unknown fields and enums, non-canonical UUIDs,
invalid timestamps, unexpected frame types, and protocol-version mismatches make LFG unavailable
without affecting local chat features. Protocol documents and live frames are size-bounded before
JSON parsing, and collection counts are bounded before allocation. The player-authored lobby note
is at most 80 characters and is always handled as inert literal text: local submissions remove
legacy formatting and unsafe Unicode controls, inbound values violating that policy are rejected,
and both LFG presentations apply the same final display guard. Notes never become component JSON,
click events, URLs, commands, logs, file paths, or process arguments. Global revisions are monotonic
but may contain gaps because private events can be invisible to a viewer. Successful REST mutations
update the immutable store immediately; a WebSocket event at the same revision is ignored.

Accepted store changes also carry a presentation-neutral origin (`LIVE`,
`SNAPSHOT`, `LOCAL_MUTATION`, or `CLEAR`) and their previous/current lobby
projection. New-party and reopened-party discovery cards are created only from
qualifying `LIVE` changes; snapshots, refreshes, reconnect synchronization,
stale revisions, and local REST mutations can update or retire existing cards
but cannot discover new ones. The persistent `LfgJoinController` owns the
single three-second countdown and submission shared by the browser and HUD
cards.

`RaidLfgKeybinds` owns ten persisted mappings. The original nine default in order to F1 through F9:
Open plus Join, Close, Leave/Disband, Party Filled, Ping, Lock/Unlock, Create, and Kick. The
action mappings run only during normal gameplay after the service reaches a
fresh online snapshot; an open screen, absent player/world, disabled service,
or duplicate RALLE key assignment makes the affected action inert. A narrow
keyboard-handler mixin consumes only top-row digits participating in an active
Create or Kick chord, preventing hotbar selection without owning unrelated
keyboard input. Chords submit on digit release while their modifier remains
held. Create maps 1–6 to Dailies, NOTG, NOL, TCC, TNA, and TWP and derives
EU/NA/AS from locally synchronized Wynncraft server labels. Kick maps 2–4 to a
captured member UUID and revalidates that member before mutation.

Two independent local booleans can replace the Create and Kick chords with a transparent,
non-pausing selector wheel. A wheel opens only from synchronized normal gameplay, owns input
for that modifier hold and selects only by direct control hover. Create submits at most once
per hold. Kick accepts another fresh left click after the previous accepted kick animation
finishes (or a failed request returns), with at most one kick request pending. Its session captures raid or lobby/member UUID
identities and revalidates current capabilities, region, lobby identity, host authority, target
membership, and pending mutations immediately before submission. Releasing the modifier,
Escape, focus loss, invalid synchronized state, or replacement by another screen cancels it.
Notification-card hit regions and the keybind Action Bar are inactive while the wheel owns input.
Both selectors render curved annular segments around an empty center. Create places each
native raid item above its short label; Kick embeds player heads in a smaller ring. Hit testing
uses the annular segments and their fixed outward envelopes, not rectangular content bounds.
Each segment independently eases outward five logical pixels over 120 ms and returns smoothly
on deselection. Geometry and content share the same viewport scale. Cached pixel scanlines
keep curved rendering crisp without recalculating the raster every frame.
Both wheels use a clean navy interior with white/gold outlines, omitting gray highlights and
black edge-depth pixels. Kick keeps three-member geometry unchanged; one member uses just
the top 120-degree segment, and two use equally sized top/bottom segments. Its roster tracks
the synchronized lobby while open, resets stale index-based hover/motion on changes, and
revalidates each UUID before submission. Accepted kicks play the bundled Realistic Explosion
sprite (17 frames, 80 ms/frame) at the submitted target's saved position and the user-selected
explosion sound once; failed requests never animate. Roster updates may arrive before the
mutation response without moving or discarding that saved effect position. A delayed response
cannot animate or change a newer wheel. Release/Escape still closes immediately.

Create captures the selected clean segment, current resource-pack raid item, and selected-font
label once into a screen-owned GPU texture, warmed on hover. During success, one composite
quad samples that texture and the bundled 64-frame dust-motion atlas; there are no runtime
random/hash calculations, pixel-by-pixel fill calls, repeated scissor/item/text draws, or GPU
readbacks. The original pixel drift (up to six logical pixels upward/rightward), opacity, and
overlap paint order are baked offline from the original animation formula. Each atlas texel
encodes two source contributors; four pages retain up to eight overlapping pixels. The 4096px
RGBA atlas occupies a fixed 64 MiB on the GPU and is warmed with the hover snapshot before
playback. Its 256px pattern exactly covers wheel coordinates -128 through 127 and repeats for
larger custom-font geometry. Eight pixels of capture padding retain outward-flying dust.
The outward movement, 80%-of-sound dissolve duration, 100 ms empty hold, and exit
creation cue remain. Snapshots are disposed on resize, prompt suspension and screen removal;
failed capture uses bounded ordinary rendering and removes the segment halfway through the
same presentation interval. Raid ItemStacks are reused for the screen's lifetime.
`GuiCaptureTargetOverride` provides the existing isolated GUI target routing for both screenshot
capture and wheel capture, without sharing their GUI render states or feature enablement.
Dust-only preparation is offline in `tools/bake_create_dust.py` (NumPy/Pillow); the complete
asset preparation entry point remains `tools/prepare_wheel_assets.py`. Provenance is packaged under
`licenses/ralle-wheel-assets/README.txt`. To run the optional native shader smoke test, set
`RALLE_GPU_TEST=1` and run `test --tests '*CreateWheelGpuTest'`. It uses an invisible OpenGL
window to check the shipped shader and premade mask alpha; actual Minecraft frame-time and
Wynntils compatibility checks still require an in-game run.

Raw presses and physical-key tick recovery both choose the enabled wheel instead of entering
the legacy chord. Closing on release cannot re-arm the release guard; closing while held waits
until both physical modifiers are released. Screen replacement also clears the active owner.

Keybind-only feedback is held in `LfgActionBarState` and rendered by the fixed
HUD overlay above the crosshair. Raid titles use the same centralized
raid-name/item mapping as the browser and notification cards. Join remains
card-driven and is intentionally absent from the Action Bar. The chat-layout
editor previews the fixed Action Bar but does not make it movable.
Create, Kick, Lock, Unlock, Ping, and Leave/Disband Action Bar feedback
prepends its fixed recognition glyph as an explicitly Vanilla-font component,
leaving the action copy in the selected interface font. Those glyphs do not
appear on notification-card or Raid LFG browser controls. A Ping activation
during the locally tracked cooldown reports the rounded-up seconds remaining.
Party-scoped keybinds report when the viewer has no synchronized Raid LFG
party, and host-only bindings distinguish that state from being a non-host
member. Party Filled queues the synchronized non-host roster through the same bounded invitation
controller only while the authoritative host lobby is full.

Automatic Raid Requeue is the tenth mapping and defaults to Unbound. It is independent of the Fox
LFG service toggle, but is inert unless explicitly bound and connected to Wynncraft. While bound,
`AutoRaidRequeueController` listens to non-overlay server game messages for the fixed Wynncraft Ready Up prompt from any
queue initiator, including the local player. Observation uses Fabric's `ALLOW_GAME` event before display
filtering or rewriting, always allowing the message through. Nicknames and their hover identities do not
restrict detection: only the raid and Ready Up text matter. Signed player chat and action bars are excluded. Announcement and Ready Up
lines may arrive together or within 40 client ticks; newer announcements replace pending ones. It stores
only the recognized fixed raid ID in `config/ralle-auto-requeue.properties`. Activation sends one
`/pf` command and follows a bounded three-menu state machine: scan the main raid area through the
first player-head listing, use the sixth-row fifth-slot Party Queue fallback when needed, select the stored raid,
then click a named Ready Up item in slots 33–35 (with a name-based fallback). Each server menu has a
three-second timeout. A narrow `Minecraft.setScreen` interception suppresses only menus expected by
that explicit state machine after vanilla has installed their container, allowing inventory packets
and validated slot clicks without displaying the GUI or blocking gameplay input. Completion,
after the Ready click, RALLE waits up to three seconds for Wynncraft to close the hidden container so
its native queue-confirmation chat can complete, then closes that owned container only as a timeout
fallback. Other timeouts, disconnect, or context loss close only the owned container and clear
session state; there are no retries or command loops.
Its `Requeue` and `Requeued` Action Bar states prepend the Vanilla-font `🔄` recognition glyph.

`LfgLockDebouncer` is shared by the keybind controller and Raid LFG screen.
Each Lock/Unlock activation toggles a desired local state and restarts a
one-second deadline. At the deadline it submits at most one ordinary
server-authoritative lock mutation, and submits nothing when the desired state
has returned to the current authoritative state. Disconnect, authority loss,
or lobby replacement clears an unsent intent. Each accepted local toggle
immediately plays the vanilla vault insert cue for Lock or vault insert fail cue
for Unlock, regardless of whether it originated from the keybind or main screen;
server completion does not replay the cue.

The keybind and persistent HUD card share one five-second disband confirmation
state, keyed by lobby and revision. Timeout, disconnect, authority loss, lobby
replacement, screen opening for keyboard prompts, or rebinding clears it.
Card-visible confirmation replaces that card's bottom action; otherwise the
Action Bar presents the prompt.

The optional party-status notification watches the authoritative projection for
the viewer becoming a lobby member. Create and Join actions initiated by the
Raid LFG screen explicitly register their next matching membership transition
and retire any existing discovery card for that lobby, so REST mutation and
live-event delivery order cannot misclassify them. Both Party Status
Notifications and the separate `Auto Pop-out` option are disabled by default on
new installs. When enabled, Auto Pop-out turns the matching
transition into a persistent card and closes the browser. Synchronized
snapshots and future non-screen local actions still qualify for the external
party-status option. This covers Discord and future keybind creation without
assigning authority to client-reported member source labels. Abandoned
registrations expire after one minute. Persistent party-status cards track
roster and lobby changes and do not passively expire while below capacity. Full
cards retain their controls for ten seconds, then use the standard 500 ms slide-out.
Repeated full updates do not extend this deadline; reopening before exit cancels
it, and refilling starts a fresh ten seconds. This also applies to manual spectator
pop-outs and cards first shown already full. Automatic full dismissal suppresses
snapshot-driven redisplay until departure or an explicit new pop-out. An authoritative
departure, kick, or disband closes the viewer's card. Every expanded browser
card also has an explicit full-width neutral `Pop out` control which creates the
same persistent HUD presentation and closes the browser.

Closing a persistent party-status card with its X suppresses automatic presentation for
that lobby across server switches and fresh synchronization snapshots. The suppression
ends after an authoritative departure; explicitly choosing `Pop out` may also restore the
card while the viewer remains in that lobby.

Notification cards reserve their top-right `20 x 20` control for a destructive
red, `10 x 10` pixel-drawn white X, optically offset one pixel up and left
within the shaded face. It removes only that card presentation and never opens
the browser, cancels a Join, leaves, or disbands. The persistent join controller
continues a dismissed countdown or submission and still owns exactly-once
terminal sound and state handling. Bottom actions are full-width Join, Joined,
status, Leave, or Disband states as applicable. A full persistent host card instead keeps
`Party filled` on the left and `Disband` on the right. Usable bound
Join, Leave/Disband, and Party Filled keys appear beside their card actions; the Close binding
is not printed beside the X. Unbound or conflicting mappings do not advertise a
nonfunctional shortcut.

The host's expanded browser cards and persistent party-status card derive a live lobby-age timer
locally from the synchronized lobby creation instant. Collapsed browser cards omit the timer. The
timer is normally omitted on another player's lobby and adds no stored timer state.

The only persisted LFG values are local opt-in, notification, sound, keybind,
HUD-placement settings, and the single last-raid ID used by Automatic Raid Requeue. Whether a card
is currently popped out remains
session-only. Credentials, snapshots, pending actions, backend overrides, and
connection state are memory-only.


`lobby.remove` accepts an optional string `reason` (at most 64 characters). A live
`lobby_expired` removal for unfilled initial recruitment shows
`Timed out!` in a non-actionable slate-blue button for two seconds, then uses the existing
500 ms exit animation. During feedback and exit, the card displays a frozen `30:00` timer,
including on spectator pop-outs. The client never infers expiry from its clock, a disconnect,
or a snapshot omission. Legacy removals still work without the reason; removed persistent
spectator cards also exit. Unknown reasons use the ordinary removal behavior.

The client opts into removal reasons with `X-Ralle-Removal-Reasons: 1` on each WebSocket
handshake. Fox sends the reason only to opted-in connections, preserving older strict clients.
The server closes initial recruitment at 30 minutes from creation unless the party has
ever reached four members; recent activity does not extend this deadline. Previously
filled parties remain exempt even after reopening.

Roster head borders in both the browser and HUD resolve known territory colors through
`GuildTerritoryColors`, matching Fox's bundled territory table (Fox `#FF8200`, Novu
`#CE4F4F`). Unknown prefixes retain the backend color; malformed fallbacks use muted gray.


### War controls and popup navigation
The local setting `war.hq-distance-keybind` defaults to `key.keyboard.left.control`;
either Ctrl works with this default. It synchronizes bidirectionally with Minecraft
Controls and polls held input while the supported guild map is open. Unbound
suppresses inspection; the feature toggle remains disabled by default.
`war.queue-self-color` persists a `#RRGGBB` value with an optional `;rainbow` suffix, default `#5555FF`. Existing solid colors load unchanged.
The settings palette uses the shared consumable color dialog shell with HSV and
hex/rainbow controls and only the signed-in account's frozen sample queue above the hex controls. The preview occupies the full inner dialog width and draws one native-font line, keeping the timer beside the territory even for a 16-character IGN. Hex input and Rainbow sit beneath it beside the compact color wheel. Apply spans the full inner dialog width below the picker body.
Apply saves the color; Escape discards the draft. Production and editor attribution
use the selected solid color or animated per-character rainbow only for the local IGN. Rainbow uses the consumable highlight hue cycle and retains the stored solid RGB when switched off. Other row formatting stays owned by Wynntils.
Settings and LFG screens consume Escape before owo can dismiss a popup and
propagate the same event to the parent. Closing a popup retains the parent screen.
Prime Minister snapshot glyphs are pre-encoded as PM, including stars-and-title
presentation; identity and rank hover retain the full title.

The public rank decoder gives an explicit boolean `prime_minister: true` precedence
over the base Fox rank in `ranks` (for example Lord). It stores `PRIME MINISTER` in
the local snapshot/cache so the existing renderer emits PM in title-bearing pills.
Absent, false, or malformed PM flags retain normal Fox-rank decoding.

### Creating LFG with an existing Wynncraft party

Creation feedback is shared by `LfgCreationFeedback` and localized under
`ralle.lfg.create.*`. The browser renders it inline without discarding the form;
number-key creation uses the Action Bar; the selector wheel uses its status area
and falls back to the Action Bar when dismissed. Both compact displays wrap long
feedback at the available screen width. `Creating party…` appears only after the
existing-party choice, immediately before submission. Cancellation is silent.
A changed imported roster reports `Your party changed. Please try again!`
without switching to solo creation.

Unavailable service feedback is `Raid LFG is unavailable. Please try again
later!`; unexpected failures use `Please try again!`. A recruiting-lobby conflict
uses `Party for this raid already exists!` and refreshes listings while retaining
the browser form. Specific access, protocol, region, occupancy, and rate-limit
reasons remain actionable. Arbitrary exception/backend diagnostics are not used
as create-result copy. Disabled browser Create controls expose their reason in
the tooltip, and offline create key presses provide local Action Bar feedback.

The service's existing pending mutation and fresh-snapshot reconciliation remain
authoritative: `Checking party status…` replaces processing feedback during an
uncertain transport outcome, and duplicate submissions remain blocked. A terminal
unconfirmed result asks the player to reconnect and check status rather than
inviting an immediate retry. Request completion never reopens a dismissed form.
Wheel failures remain readable for 3.5 seconds, with Escape still available, and
never play success sounds or animations.

`WynncraftPartyCreation` is the shared pre-create path for the browser, create
chord, and selector wheel. On supported optional Wynntils installations it reads
`Models.Party` without requesting data or issuing commands. Wynntils' party
model extracts canonical names from nickname hover metadata in party events.
Only a known local party leader with other members gets the confirmation. The
browser mounts the compact confirmation over its existing create form; keybind
creation mounts the same framed content over gameplay. Escape cancels creation.
Party, solo, and cancel choices are explicit and never persisted. Oversized
parties cannot be imported; no subset is silently selected. After confirmation,
the connection, viewer, party leader, and complete roster are rechecked.

The optional `party_members` create-request array contains at most three
distinct canonical Minecraft usernames, excluding the host. Empty imports omit
the field, preserving ordinary protocol-v1 requests. A backend without the new
field rejects the request; the client never silently retries it as solo creation.
Party import therefore requires deploying the matching Fox backend change.

Import rejection feedback distinguishes an occupied guest (`PARTY_MEMBER_ALREADY_ACTIVE`),
an unresolved identity (`PARTY_IDENTITY_UNRESOLVED` or `PLAYER_NOT_FOUND`), and an invalid
roster (`INVALID_PARTY`). Failed HTTP responses log their status, bounded error category,
and canonical `X-Request-ID` locally for correlation with Fox diagnostics. Mutation
failures also log the action and exception type. Bodies, credentials, rosters, notes,
and arbitrary exception messages are excluded; no diagnostic upload is performed.

Fox resolves every imported identity through Wynncraft, accepts guests from any
guild or no guild, and reserves host plus guests atomically under the existing
capacity/uniqueness rules. Guests use the existing `MANUAL` member source; no
session, eligibility, or player authorization is conferred by being imported.
The party roster is a host-declared reservation, not server-verified proof of
Wynncraft party membership. Guildless guests have protocol-v1 presentation
metadata UUID `00000000-0000-0000-0000-000000000000`, name `No guild`, tag `-`,
and neutral color `#697487`; that marker is never accepted for authentication.
Ambiguous or unresolved names fail rather than being guessed as another account.
Guest identity failures, duplicate identities, host duplication, existing LFG
occupancy, and oversized parties leave no partial lobby. Accepted replays return
the original transaction. No data is saved or transmitted before an explicit
create action. Without a supported party-data provider, ordinary creation remains
available and no existing-party prompt is inferred.
