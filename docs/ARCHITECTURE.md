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
- `ui.owo`: the owo-lib adapter that renders the settings registry.
- `chat`: future Minecraft chat integration and Wynntils compatibility boundary.
- `lfg`: future protocol, authentication, live connection, lobby domain, and
  party-automation boundary. It must remain inert until explicitly enabled.
- `platform`: future Fabric/Minecraft adapters such as commands, keybinds,
  connection lifecycle, local persistence, and clickable chat notifications.

## Foundation invariants

- No feature is enabled or registered by this skeleton.
- Initialization performs no network requests and changes no game behavior.
- owo-lib is contained behind `SettingsScreenFactory`; future settings register
  through `SettingsRegistry` rather than constructing owo components directly.
- Feature IDs and setting/category IDs are validated and unique.
- Registries are sealed after bootstrap to catch accidental late mutation.
- Minecraft-specific hooks should use supported APIs before mixins.

The eventual Fox FastAPI service is a separate repository and remains the sole
authority for LFG state. No backend implementation belongs in this client.
