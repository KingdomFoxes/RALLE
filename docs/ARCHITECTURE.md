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
- `sound`: client-only registered UI sound events and playback adapters. Chat
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
  Packaged RALLE sounds use original processed-xylophone assets and perform no
  network activity.
- `lfg`: strict Fox protocol, authentication, live connection, immutable lobby
  projection, and Raid LFG orchestration. It remains inert until enabled and
  connected to Wynncraft.
- `war.consumables`: local, immutable ordered displayed-name rules and the
  container-slot highlight service. The service is inert unless its setting is
  enabled, a play connection exists, and the host is a real Wynncraft domain.
- `war.hqdistance`: an optional exact-version Wynntils adapter around immutable
  territory snapshots, a pure bidirectional graph projection, provisional queue
  estimation, cached inspection state, and collision-aware rendering. It consumes
  only data Wynntils already maintains and performs no polling or backend calls.
- `war.queue`: bounded, memory-only sender attribution projected from verified
  guild-chat envelopes onto Wynntils' existing Guild Attack Timer render tasks.
  Pure identity, parsing, tracking, and formatting code is separated from the
  optional exact-version event/model adapter and HUD-scoped mixin.
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
cache is cleared whenever a new snapshot is published.

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
Wynntils version is exactly 4.2.7 for Minecraft 1.21.11. The compile-only
development dependency is pinned to Modrinth project `dU5Gb9Ab`, Fabric version
artifact `jeBTZ3Zn`; Wynntils is never bundled.

The optional `TerritoryPoi#renderAt` tail hook is restricted at runtime to
`GuildMapScreen`. It draws only for the hovered territory while the
configured inspection key is down (either Ctrl by default), using Wynntils' transformed center, zoom scale, and active map
scissor. It does not intercept input. A known active attack timer suppresses both
RALLE labels so Wynntils' real timer remains authoritative. Labels remain anchored
above and below the centered guild tag or HQ crown without collision or territory
fit checks, including when zoomed out. Overlap with names and neighboring
territories is intentional; the map viewport scissor still applies. Labels
use Wynntils' own font renderer and four-direction outline at native size with
pixel-aligned origins; they never shrink to fractional scales to fit a territory.
Unexpected adapter or linkage failures are logged once and disable only this
overlay for the remainder of the client session.

`WynntilsTerritorySnapshotSource` projects the current guild identity and
advancement-backed territory information without retaining mutable Wynntils
objects. Links are normalized as bidirectional and references to missing nodes
are ignored. `TerritoryRouteCalculator` performs breadth-first search from the
single owned HQ through territories regardless of their owner. Owned destinations use the same distance result,
representing the documented hypothetical ownership change. Missing identity,
missing or multiple owned HQs, or globally unavailable input yields `Unknown`.
Missing information or disagreement between advancement and profile ownership
marks only the affected territory as unreliable. Unrelated stale territories do
not invalidate a verified route. Unreliable destinations and HQs yield `Unknown`;
routes never traverse unreliable nodes, and an unsuccessful search that reaches
one yields `Unknown` instead of claiming disconnection. A genuinely
disconnected destination also yields `Unknown`; HQ yields `0` without a
duration.

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
updated if necessary.

## War queue attribution

`war.queue-attribution-enabled` defaults to false and persists in
`config/ralle.properties`. It is visible but unavailable unless Wynntils is
exactly 4.2.7 for Minecraft 1.21.11, using the same compile-only artifact and
compatibility detector as HQ Distance. Enabling it never changes Wynntils'
configuration: the player must separately enable Wynntils' Guild Attack Timer
overlay, and a sender must supply the guild defense announcement for attribution
to be observable.

While enabled in an active Wynncraft world, a lazily registered Wynntils Match
listener validates the complete current guild indicator, rank pill, speaker, and
body envelope before accepting `{territory} defense is {level}`. All six pinned
defense values are accepted, and the territory must equal a canonical Wynntils
territory name after only narrow whitespace normalization. Character nicknames
resolve through hover metadata applying to the speaker span; direct names must be
valid Minecraft IGNs. The observer never cancels or edits chat and performs no
commands, polling, HTTP requests, or backend work.

Announcements may wait up to ten seconds for the matching timer model entry.
First observed sender wins for one continuously active territory countdown;
duplicates are idempotent and conflicts do not overwrite it. Attribution follows
timer-record replacement and end-time drift, but is dropped on observed absence,
expiry, a verified capture message, setting disable, disconnect, world/server or
character-selection transition, or account/guild/character identity change.
Pending and active metadata are each capped at 512 territories. No component,
chat history, or completed attribution is persisted. A cancellation and requeue
that occurs entirely between observations with an indistinguishable timer cannot
be proven to be a new generation.

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

Development builds expose `/ralle testqueuetimers` only while the feature is
enabled in an active supported Wynncraft session. It toggles three synthetic
local, remote, and unknown countdown rows by augmenting only the list value used
inside the overlay render call. The sentinel timers are replaced with styled
sample tasks before drawing, expire after roughly three minutes, and never enter
Wynntils' timer or defense models or cause a server command or message.

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

Development environments alone register `/ralle testmsg`. It posts fixed local
Strategist samples for `maxkarson` with and without the star/title gap and does
not consult the internal-rank cache. Release builds continue to expose only the
three public `/ralle` subcommands.

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
Its latest valid outgoing `/msg` recipient and last selected stable chat type are
connection-local memory only. A new empty ChatScreen restores that type without
overriding command-key input or drafts, while disconnecting clears both values.
The Fabric outgoing-command observer does not mutate or resend commands. The
ChatScreen integration cycles Guild, Party, the latest DM when known, and
prefix-free All Chat, consuming only an unmodified Tab on an empty input or an
exact RALLE-inserted prefix and leaving edited drafts and normal command
completion to Minecraft.

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
`https://kingdomfoxes.com/api/ralle/v1`. There is no runtime setting, JVM property, or automatic
fallback. Explicitly constructed development and test gateways may use insecure HTTP and WebSocket
transports only for loopback hosts. The JDK gateway is pinned to HTTP/1.1 so local requests do not
attempt an `h2c` upgrade that a local HTTP server may not support.

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
queue initiator, including the local player. Signed player chat is excluded. Announcement and Ready Up
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
roster and lobby changes and do not passively expire, but an authoritative
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
