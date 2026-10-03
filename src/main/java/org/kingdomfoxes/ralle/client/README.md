# Fox-exclusive local settings

`Internal guild ranks`, the main `Enable Raid LFG` toggle, and `KoF rank colors`
require a confirmed Kingdom of Foxes (`FOX`) guild membership for the current
Wynncraft connection. Their setting names carry an accent-colored
`(Fox exclusive)` suffix in category pages and search results. Unavailable
controls and their copy are gray; the suffix retains the current accent color.
Do not add a separate guild-access explanation beneath these setting rows.
The existing Show Who Queued dependency still applies to KoF rank colors.

On each Wynncraft login, `FoxGuildAccess` starts locked and checks the signed-in
Minecraft UUID through the public
[Wynncraft player profile API](https://docs.wynncraft.com/modules/player/get-player).
The reply must match that UUID and contain guild prefix `FOX` (case-insensitive).
Guildless and other guild members remain locked. Pending, unavailable, hidden,
and invalid profiles never grant access. Failed requests may retry twice, at
least 30 seconds apart; each request has a 12-second timeout and a 256 KiB body
limit. No requests are made on other servers or before joining Wynncraft.

Disconnect and reconnect discard the local membership result. Late replies
from a previous connection cannot unlock the current session. Membership is
not saved to disk. The settings screen refreshes when the result changes,
including while displaying search results.

`SettingsRegistry.requireAccess` gates both control availability and the
Boolean setting's effective `value()`, so existing feature hooks cannot act on
saved/default-enabled values while locked. The saved preference remains local
and survives an unrelated config save; a confirmed Fox member can use it again
without reconfiguring. Raid LFG defaults on but stays effectively off until
current Fox membership is confirmed; internal ranks and KoF rank colors default off.

This is a local feature-access check only. It neither obtains Fox credentials
nor authorizes LFG actions. Fox's backend remains responsible for Minecraft
session ownership, current alliance eligibility, and all LFG permissions.

Every Wynncraft client uses the existing Raid LFG authentication and `/live`
connection even while the local LFG toggle is disabled or locked. This lets
Fox's existing `live.connected` and `live.disconnected` logs identify guildless
users too. The toggle continues to gate LFG actions, notifications, sounds,
and party commands. Auth and snapshot viewer identities allow a null guild;
guildless snapshots grant no LFG capabilities. Other servers make no requests.
