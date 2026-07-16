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
`/ralle lfg` opens a disconnected owo-lib browser prototype populated with
fixed fake lobby data for layout evaluation. Live Raid LFG is not implemented
yet, and every feature defaults off.

## Development

Requirements: Java 21.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
