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
  current hook only applies the opted-in chat rectangle and leaves message
  content and ordinary vanilla rendering behavior untouched. Local chat
  customization is available in singleplayer and on any multiplayer server.
- `lfg`: future protocol, authentication, live connection, lobby domain, and
  party-automation boundary. It must remain inert until explicitly enabled.
- `platform`: future Fabric/Minecraft adapters such as commands, keybinds,
  connection lifecycle, local persistence, and clickable chat notifications.

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
values fall back to their declared defaults. The current settings are inert
until their corresponding chat and Raid LFG vertical slices consume them.

Custom HUD rectangles are stored separately in
`config/ralle-hud-layout.properties` as normalized coordinates and dimensions.
The registry currently exposes only the v1 chat box. Removing its custom entry
restores the live vanilla chat position and dimensions rather than copying a
snapshot of the vanilla options.

## UI model development

RALLE's settings, chat-layout editor, and Raid LFG screen skeletons are owo UI models in
`src/main/resources/assets/ralle/owo_ui`. Java code binds interactions and
populates dynamic settings rows, the draggable chat rectangle, lobby cards,
and custom-rendered components.

In a development client, open either screen and press Ctrl+F5. Choose its XML
file as the hot-reload source. After saving XML changes, close and reopen the
RALLE screen to load the updated model without rebuilding or restarting the
game. owo stores these development-only file associations under the run
directory's `config/owo_ui_hot_reload_locations.json5`; release builds continue
to load the packaged XML assets.

The eventual Fox FastAPI service is a separate repository and remains the sole
authority for LFG state. No backend implementation belongs in this client.
