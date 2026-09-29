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
UUID matching, grant validation, revision checks, and disabled-feature network
gating still apply. A grant does not imply a selected style.

Shared personal style selection uses Fox's cosmetic challenge/complete and
authenticated `PUT /me/style` endpoints. Fox validates current grants at write
time and publishes the selected ID in later lookups for other RALLE clients.
The Nameplate Color dropdown lists only styles allowed by the current grant;
the selected entry is non-actionable, and an empty grant has an explicit empty
state. Older grants-only servers remain readable but must upgrade for saving.
