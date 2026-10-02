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

Responses without cache TTL hints use a local 30-second refresh interval. Older
responses may supply root/player `cache_ttl_seconds` and `selected_style_id`;
supplied values remain strictly validated, and the shortest TTL is used.
UUID matching, grant validation, revision checks, and Wynncraft-only network
gating still apply. Nameplates are always on; bounded public lookups run on client
ticks for the local account and visible world/LFG players. The removed local
`cosmetics.nameplate-cosmetics` toggle is ignored, including old saved `false`
values. A grant does not imply a selected style.

TTL expiry schedules a bounded refresh without removing the last accepted
cosmetic presentation. Keep it visible while a lookup is pending or fails;
an unchanged successful response renews freshness without changing presentation.
A newer accepted style, cleared selection, or grant revocation replaces it.
Retained revisions still reject older replies after TTL expiry. The cache remains
bounded to 256 identities and is cleared on disconnect, account/server change,
or disable; retained cosmetic grants never authorize LFG operations, and Fox
continues to validate personal style mutations.

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
`2 logical pixels`, and `0.5 logical pixels`. New configurations default to
`1 logical pixel` for every style. Existing saved choices, including the legacy
recipe value, remain compatible.

`cosmetics.show-own-nametag` is a local, disabled-by-default Supporter setting.
Once enabled, it reveals the local player's normal nameplate in both F5 views
and the survival/creative inventory character preview. First-person world
rendering and unrelated entity previews retain their normal visibility rules.
The control follows the existing cached-grant settings lock; rendering never
initiates a lookup or persists anything to Fox. The saved visibility preference
can also work outside Wynncraft, where no Fox material is resolved.

The visibility hook leaves Minecraft's `EntityRenderer.getNameTag` and
`AvatarRenderer` score extraction/submission intact. In 1.21.11, player display
names use the received scoreboard-team prefix, suffix, color, and original text
styles; below-name scores come from the same player state used for other players.
RALLE's selected material is applied through the existing renderer exactly as it
is for other players. The inventory viewport grows symmetrically, bounded by the
screen, to accommodate the actual text without moving/scaling the character.
Inventory extraction is scoped and restored even after an exception, so it
cannot leak visibility into the first-person world pass.

This preserves data received by the local client, rather than constructing a
name from the IGN, tab-list name, Wynncraft API, or guessed rank symbols. There
is no general client-only way to recover recipient-specific decorations or
separate label entities that a server sends exclusively to other players.
Wynntils may still replace or hide tags through its own normal nameplate options.
Validate Wynncraft prefixes, resource-pack glyphs, scores, both F5 directions,
inventory mouse-follow behavior, and Wynntils compatibility in-game before release.

## Material and head rendering

The shared GUI/world material texture uses the cached GPU linear sampler with
clamped edges. Interpolation smooths uneven texel bands when the complete recipe
is fitted to a scaled plate; texture dimensions, CPU frame generation and the
30 Hz update limit are unchanged. No mipmaps or extra frame buffers are added.

Supporter roster plates and world/inventory nametags have a one-logical-pixel
white border around the complete plate, independent of material resolution.
GUI borders sit inside the plate bounds; world borders extend around the text
background without reducing the vanilla text area. World plates measure both
the full label's advance and Minecraft's prepared glyph geometry, including
resource-pack icons whose ink extends beyond their advance. Two logical pixels
of horizontal padding sit inside the separate white border. The inventory
viewport fits the extracted label bounds symmetrically without moving the character.
`AvatarNameplateMixin` carries cached identity through a tightly scoped render
callback rather than drawing from the early extracted label. The scope includes
Wynntils' cancellable replacement callback. `NameTagMaterialMixin` decorates the
common final `NameTagFeatureRenderer.Storage.add` path, so a replacement label
includes its actual marker icon and suffix in measurement. Only the row containing
the canonical IGN (or the original label) receives material; score, level and role
rows retain their existing presentation. Nested, disabled and failed callbacks
restore the preceding scope without leaking a player's cosmetics to another row.
Material and border submissions remain in Minecraft's normal entity collection
(order 0). Only the decorated label moves to order 1, after that collection's
models and custom geometry. The label retains its exact vanilla pose, attachment,
lighting, visibility flags, and single submission; scores and undecorated labels
retain their original path. Geometry is captured immediately before the final
storage submission restores its pose, after attachment, camera and scale have
been applied, including Wynntils' scale hook. No independent guessed transform
or duplicate early plate remains. Material depth remains behind the glyphs. A prepared
glyph-measurement failure falls back to padded advance width instead of dropping
the plate or inventory preview, and logs one local warning. Submission failures
also log only once rather than failing silently or flooding the render log.
Decode embedded legacy formatting before tinting so codes such as `§f` never
become a visible prefix letter, preserving fonts, glyphs, and non-color styles.
Lettering remains white without a username glyph outline. Decorated world and
inventory labels carry Minecraft's native drop shadow in their glyph styles:
the standard quarter-brightness shadow and font-defined offset, with alpha
following each normal/see-through pass. This survives deferred nametag rendering,
which disables the draw-shadow flag. Other rows retain their original shadow styles.

Notification heads and their enlarged Supporter preview draw one complete
material quad first with a one-pixel white outer edge, then a one-pixel guild-color ring inset by the white edge and two-pixel
material frame, then the skin. Both rings fit inside the existing 20-pixel head
bounds (the skin is 12 pixels). The inner ring always uses the guild territory
color; cosmetic palette colors apply only to the outer material frame. This replaces four scissored overlays that covered
the guild ring and could leave stray edges when scaled. Browser heads retain
their existing one-pixel guild ring and 16-pixel skin.

Kick modifier-wheel heads reuse the same material-frame renderer with the cached
Fox-selected style and local material resolution. Their existing 18-pixel bounds
contain the one-pixel white edge, two-pixel material frame, one-pixel guild ring,
and 10-pixel skin;
players without a selected style retain the ordinary guild ring and 16-pixel skin.
Rendering never initiates cosmetic requests; the existing tick lookup already
includes synchronized lobby members.
