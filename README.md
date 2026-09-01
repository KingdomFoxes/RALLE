# RALLE

Raid Alliance Logistics Liaison Equipment is a client-side Fabric mod for
Minecraft 1.21.11. The current foundation includes lifecycle composition, a
RALLE-owned feature and settings registry, local settings persistence, and a
searchable owo-lib settings screen opened with `/ralle settings`. Its chat
section includes a dedicated editor for moving, resizing, and restoring the
single chat box; placements are normalized across resolution and GUI-scale
changes and apply in singleplayer or on any server. Compact chat, empty-line
stacking, message direction, horizontal text alignment, and text-shadow modes
are implemented as independently disabled
local options. Transparent chat screenshot selection also supports an optional
per-line text-snapped overlay and text-width image crop, plus processed-xylophone
count and successful-copy feedback. Screenshotting and its sounds are disabled
by default on new installs.
OW-like Chat Tabbing is another disabled-by-default local input option. In an empty
chat, unmodified Tab prepares Guild, Party, the latest complete outgoing `/msg`
recipient when known, and prefix-free All Chat in sequence; editing the prepared
prefix restores vanilla autocomplete, and RALLE never sends the prepared command
automatically. New empty chat screens restore the last selected type. That type
and the latest valid recipient reset when disconnecting and are never saved to disk.
Persistent Chat is another disabled-by-default option. When enabled, it keeps
up to the selected 300, 500, 1000, or 1500 displayed messages and wrapped lines
while moving between multiplayer servers and singleplayer worlds. This history
exists only in memory, may mix messages from different worlds without labels,
and disappears when Minecraft closes. RALLE never reloads it from logs or
writes it to another file, and manual F3+D clearing remains permanent.
Guild Rank Style is a local Wynncraft guild-chat choice with unchanged title
pills as its default. It can replace the six standard ranks with zero to five
classic stars, or render those stars and the current title inside one continuous
cyan pill. Internal Guild Ranks remains independent: when enabled, the combined
style keeps the stars from the player's Wynncraft guild rank and uses the
resolved Fox title for the title portion.
`/ralle hud` opens the layout editor with every currently enabled editable HUD
element available; the About page exposes the same action as `Edit HUDs`.
`/ralle lfg` opens the live, authenticated owo-lib Raid LFG browser when the
feature is enabled on Wynncraft. Hosts can disband, kick with a
hold-to-confirm roster interaction, lock or reopen joining, and ping current
party members. Eight additional action shortcuts cover Join, Close,
Leave/Disband, Party Filled, Ping, Lock/Unlock, Create, and Kick; Create and Kick use
top-row-number chords and report their keybind-only feedback through a fixed
Action Bar above the crosshair. Notification cards use a red top-right X for
presentation-only dismissal, including during a Join countdown, and show usable
bound keys beside their Join, Leave/Disband, and Party Filled actions. Action Bar feedback
pairs Vanilla-font recognition glyphs with the selected interface font,
including a remaining-time message when Ping is on cooldown, while rapid
Lock/Unlock toggles are coalesced for one second before any backend mutation.
Automatic Raid Requeue is a separate, unbound-by-default Wynncraft shortcut. While bound, it
remembers the fixed raid name from the most recent server Ready Up chat prompt, whoever initiated the queue
in `config/ralle-auto-requeue.properties`. Pressing it runs one bounded, headless `/pf` flow,
selects that raid from the main Party Finder page or its Party Queue fallback, and clicks Ready Up
while ordinary movement remains available. It does not loop or keep a menu visible.
On new installs, Raid LFG, its notifications, its sounds, and all ten LFG and
Automatic Raid Requeue controls default to disabled or Unbound.
Saved settings remain authoritative for existing users.

The first connection after a new installation posts one local RALLE onboarding
message with clickable settings, LFG, and HUD commands. Its numeric sent marker
is stored in `config/ralle-onboarding.properties`; existing RALLE configs or
saved vanilla RALLE keybinds are migrated as already seen.

## Development

Requirements: Java 21.

Development runs additionally expose `/ralle testmsg`, which posts two local
Strategist guild-chat samples for comparing the combined pill with and without
a space between its stars and title. The command is not registered in release
builds.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
