# 0.1.8-tom theme editor test build

This temporary test build adds a companion theme editor to `/ralle settings`. It edits the selected catalog theme's background, outline, and accent in the shared runtime palette so existing palette consumers preview changes immediately. Drafts are keyed by catalog ID and remain in memory across settings screens and worlds; restarting Minecraft clears them. The editor does not add saved settings, theme choices, commands, requests, or backend behavior.

Hex fields accept complete `#RRGGBB` values. Incomplete or invalid text remains visible while the last valid color stays active. The HSV picker follows the active field. Copy exports the catalog name and creator with the effective RGB values as formatted JSON; Default uses `RALLE Default` and `RALLE`.

The Copy control uses a bundled, offline ToolyTom head. The original Mojang skin and the extracted head are in `assets/ralle/textures/gui/theme/`; provenance and the resolved UUID are documented in that directory's README.
