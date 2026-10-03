# Modrinth login update notice

Every multiplayer login (Wynncraft and other servers) checks for a published
RALLE version newer than the installed Fabric metadata version and compatible
with the running Minecraft version and Fabric. Singleplayer does not check.
The highest semantic version wins regardless of API ordering; listed release,
beta and alpha versions are eligible when newer. Equal versions, build-metadata
changes, older versions, incompatible listings and non-semantic development
versions do not produce notices.

`ModrinthUpdateLookup` uses the public Modrinth v2
[`GET /project/ralle/version`](https://docs.modrinth.com/api/operations/getprojectversions/)
endpoint, filtered by loader and Minecraft version, with `include_changelog=false`.
It needs no API key or player identity. It sends an identifying RALLE User-Agent,
uses the shared HTTP transport, and bounds each check to 12 seconds and 512 KiB.
Network reads and JSON parsing run away from the client thread. Invalid, private,
unpublished, missing, rate-limited and unavailable responses stay quiet.

`UpdateNotice` consumes results on the client thread and delivers at most one
notice per login. Checks, including failed checks, are limited to once per minute;
rapid reconnects reuse public metadata or the existing in-flight request. No
notice is posted after disconnect. There are no automatic retries during a login,
saved data, credentials, settings, commands, update downloads or Fox requests.

`UpdateMessages` passes the localized body through `RalleChatMessages`:
`RALLE: A new version has released! <version name>.` The Modrinth version name
uses the shared gold, bold, underlined clickable span and opens that version's
Modrinth page, which displays its changelog. Links use a validated base62 version
ID under the fixed RALLE project URL; API-supplied links are never opened. Version
names have control/formatting characters removed and are capped at 120 codepoints.
The prefix and link follow the player's selected RALLE theme.

Before release, verify login, message wrapping and the version link in-game on
Wynncraft and another multiplayer server, with and without Wynntils. Also verify
disconnect during a check and quiet behavior while offline or up to date.
