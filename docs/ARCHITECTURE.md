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
- `chat`: Minecraft chat integration and Wynntils compatibility boundary. The
  opted-in display projection implements the 45-second compact-chat window and
  consecutive blank-line stacking without replacing vanilla source messages or
  signatures. Narrow graphics transforms implement message direction,
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
  in singleplayer and on any multiplayer server.
- `sound`: client-only registered UI sound events and playback adapters. Chat
  selection injects this narrow port, while its Minecraft implementation owns
  parent-setting gates, count-to-cue mapping, rate limiting, and coalescing.
  Raid LFG discovery cards own their lifecycle cues. The shared join-result
  presentation plays the vanilla respawn-anchor charge variants after a
  confirmed viewer join from either the main screen or a HUD card. The main
  screen plays the first amethyst resonance after confirmed party creation and
  the deplete variants after an explicit successful Leave. Later members
  joining the viewer's lobby retain the amethyst occupied-slot cue. Cancelling
  a join countdown is silent.
  Packaged RALLE sounds use original processed-xylophone assets and perform no
  network activity.
- `lfg`: strict Fox protocol, authentication, live connection, immutable lobby
  projection, and Raid LFG orchestration. It remains inert until explicitly
  enabled and connected to Wynncraft.
- `platform`: Fabric/Minecraft adapters such as commands, keybinds, connection
  lifecycle, local persistence, and future clickable chat notifications.

## Foundation invariants

- No chat or Raid LFG behavior is enabled by this foundation.
- Initialization performs no network requests and changes no game behavior.
- owo-lib is contained behind `SettingsScreenFactory`; future settings register
  through `SettingsRegistry` rather than constructing owo components directly.
- Feature IDs and setting/category IDs are validated and unique.
- Registries are sealed after bootstrap to catch accidental late mutation.
- Minecraft-specific hooks should use supported APIs before mixins.

## Local settings

`/ralle settings` opens the owo-lib adapter over RALLE-owned category and setting
models. Values are stored in `config/ralle.properties`; invalid or obsolete
values fall back to their declared defaults. Chat settings are consumed by the
local chat integration. The Raid LFG opt-in gates the persistent Fox client
service, while its shortcut remains unbound until configured.

Custom HUD placements are stored separately in
`config/ralle-hud-layout.properties`. Resizable elements such as the v1 chat
box persist normalized coordinates and dimensions. Fixed elements such as Raid
LFG notification cards persist a left/right side anchor and normalized vertical
position; their logical size is not configurable. Removing the chat placement
restores the live vanilla position and dimensions, while removing the
notification placement restores its eight-pixel bottom-right default.

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

During private feature development, the packaged protocol-v1 base URL is
`http://127.0.0.1:8001/api/ralle/v1`, matching the single-worker local Uvicorn service. The intended
production URL remains `https://kingdomfoxes.com/api/ralle/v1` as a named constant for the release
switch. Either build may override its default with the `ralle.lfg.baseUrl` JVM property. Insecure
HTTP and WebSocket transports are accepted only for loopback hosts; this is intentionally not a
player setting. The JDK gateway is pinned to HTTP/1.1 so local requests do not attempt an `h2c`
upgrade that Uvicorn does not support.

Authentication uses `POST /auth/challenge`, Minecraft's session `joinServer` proof, then
`POST /auth/complete`. The issued bearer credential is never persisted. `GET /lobbies` provides a
complete authorized snapshot; create, join, leave, disband, kick, lock/unlock, and ping use
`POST /lobbies...` with a fresh
UUID `Idempotency-Key` per player action. A failed transport attempt is retried once with the same
key. The `WS /live` connection sends the bearer credential in its `Authorization` header and must
deliver a complete `snapshot` frame before any `lobby.upsert` or `lobby.remove` frame.

Kick, lock/unlock, and ping remain Fox-authoritative host actions. An accepted kick removes the
member, applies a 120-second rejoin block, and causes exactly one bounded `/party kick <IGN>`
command on the host client. Recipient-scoped `party.ping` and Discord-originated
`party.kick-command` frames are ephemeral, are never replayed after reconnect, and do not advance
the immutable lobby projection. Ping is limited to once per 30 seconds per lobby and is delivered
to current members other than the host; linked Discord members are notified through the same
FastAPI-owned outbox flow.

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
without affecting local chat features. Global revisions are monotonic but may contain gaps because
private events can be invisible to a viewer. Successful REST mutations update the immutable store
immediately; a WebSocket event at the same revision is ignored.

Accepted store changes also carry a presentation-neutral origin (`LIVE`,
`SNAPSHOT`, `LOCAL_MUTATION`, or `CLEAR`) and their previous/current lobby
projection. New-party and reopened-party discovery cards are created only from
qualifying `LIVE` changes; snapshots, refreshes, reconnect synchronization,
stale revisions, and local REST mutations can update or retire existing cards
but cannot discover new ones. The persistent `LfgJoinController` owns the
single three-second countdown and submission shared by the browser and HUD
cards.

The optional party-status notification watches the authoritative projection for
the viewer becoming a lobby member. Create and Join actions initiated by the
Raid LFG screen explicitly register their next matching membership transition
and retire any existing discovery card for that lobby, so REST mutation and
live-event delivery order cannot misclassify them. They remain suppressed by
default; the separate `Auto Pop-out` option instead turns the matching
transition into a persistent card and closes the browser. Synchronized
snapshots and future non-screen local actions still qualify for the external
party-status option. This covers Discord and future keybind creation without
assigning authority to client-reported member source labels. Abandoned
registrations expire after one minute. Persistent party-status cards track
roster and lobby changes and do not passively expire, but an authoritative
departure, kick, or disband closes the viewer's card. Every expanded browser
card also has an explicit full-width neutral `Pop out` control which creates the
same persistent HUD presentation and closes the browser.

The only persisted LFG values are local opt-in, notification, sound, keybind,
and HUD-placement settings. Whether a card is currently popped out remains
session-only. Credentials, snapshots, pending actions, backend overrides, and
connection state are memory-only.
