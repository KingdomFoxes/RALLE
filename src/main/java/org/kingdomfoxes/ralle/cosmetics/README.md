# Fox grant lookup compatibility

## Bundled color presets

The six supplied liquid-look JSON exports are packaged unchanged under
`assets/ralle/nameplate_presets/`, named by their existing style IDs.
`NameplateStyle` reads their `dark`, `gold`, `light`, and `resolution` values
from the bundled classpath resources. The shared liquid animation parameters
are validated against `LiquidMaterial`. Existing style IDs, labels, order, and
role requirements remain in the catalog.

Export preview fields (`ign`, `guild`, `font`, `supporter`, `paused`, and
`timeSeconds`) do not set player identity, grant access, or override live
animation and viewer settings. These files define appearance, not entitlement
or synchronized selection. They are bundled presets, not a new user-config or
resource-pack override interface.

## Grant responses

`CosmeticLookupJson` accepts Fox's current v1 lookup body containing `version`
and ordered `players` entries with `minecraft_uuid`, `grants`, and `revision`.
An absent `selected_style_id` means no synchronized selection was supplied;
it must not invalidate otherwise valid website-assigned grants.

Responses without cache TTL hints use a local 30-second cache lifetime. Older
responses may supply root/player `cache_ttl_seconds` and `selected_style_id`;
supplied values remain strictly validated, and the shortest TTL is used.
UUID matching, grant validation, revision checks, and Wynncraft-only network
gating still apply. Nameplates are always on; bounded public lookups run on client
ticks for the local account and visible world/LFG players. The removed local
`cosmetics.nameplate-cosmetics` toggle is ignored, including old saved `false`
values. A grant does not imply a selected style.

Shared personal style selection uses Fox's cosmetic challenge/complete and
authenticated `PUT /me/style` endpoints. Fox validates current grants at write
time and publishes the selected ID in later lookups for other RALLE clients.
The Nameplate Color dropdown lists only styles allowed by the current grant;
choosing the selected entry closes the dropdown without a mutation. Older
grants-only servers remain readable but must upgrade for saving.

## Supporter settings

Supporter remains navigable for every account. The enlarged roster/head previews
always render using the cached selected style or the bundled gold sample. They
never authenticate or grant access. Missing identity or an empty grant covers
only the Supporter content viewport (or its search-result content) with a
60%-transparent navy pane (40% opacity) and a centered framed-navy RALLE box
containing the gold message `These features are locked to supporters!`. The compact
box centers each line horizontally and centers the visible text block vertically.
Sidebar navigation, search and other settings remain usable.
Supporter, Contributor and Admin grants unlock the controls even before a style
is selected. Controls and dropdown actions recheck access as cached grants change;
Fox still validates the current grant when saving the selected style.

Material Resolution remains a local preference with labels `1 logical pixel`,
`2 logical pixels`, and `0.5 logical pixels`. Existing saved values and the
recipe-derived default remain compatible.

## Material and head rendering

The shared GUI/world material texture uses the cached GPU linear sampler with
clamped edges. Interpolation smooths uneven texel bands when the complete recipe
is fitted to a scaled plate; texture dimensions, CPU frame generation and the
30 Hz update limit are unchanged. No mipmaps or extra frame buffers are added.

Notification heads and their enlarged Supporter preview draw one complete
material quad first, then a one-pixel guild-color ring inset by the two-pixel
material frame, then the skin. Both rings fit inside the existing 20-pixel head
bounds (the skin is 14 pixels). This replaces four scissored overlays that covered
the guild ring and could leave stray edges when scaled. Browser heads retain
their existing one-pixel guild ring and 16-pixel skin.
