# RALLE

Raid Alliance Logistics Liaison Equipment is a client-side Fabric mod for
Minecraft 1.21.11. The current foundation includes lifecycle composition, a
RALLE-owned feature and settings registry, local settings persistence, and a
searchable owo-lib settings screen opened with `/ralle settings`. Its chat
section includes a dedicated editor for moving, resizing, and restoring the
single chat box; placements are normalized across resolution and GUI-scale
changes and apply in singleplayer or on any server while chat customization is
enabled. Compact chat, empty-line stacking, message direction, horizontal text
alignment, and text-shadow modes are implemented as independently disabled
local options. Transparent chat screenshot selection also supports separately
disabled processed-xylophone count and successful-copy feedback.
`/ralle lfg` opens the live, authenticated owo-lib Raid LFG browser when the
feature is explicitly enabled on Wynncraft. Hosts can disband, kick with a
hold-to-confirm roster interaction, lock or reopen joining, and ping current
party members. Eight additional unbound action shortcuts cover Join, Close,
Leave/Disband, Party Filled, Ping, Lock/Unlock, Create, and Kick; Create and Kick use
top-row-number chords and report their keybind-only feedback through a fixed
Action Bar above the crosshair. Notification cards use a red top-right X for
presentation-only dismissal, including during a Join countdown, and show usable
bound keys beside their Join, Leave/Disband, and Party Filled actions. Action Bar feedback
pairs Vanilla-font recognition glyphs with the selected interface font,
including a remaining-time message when Ping is on cooldown, while rapid
Lock/Unlock toggles are coalesced for one second before any backend mutation.
Every feature still defaults off.

## Development

Requirements: Java 21.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
