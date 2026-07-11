# RALLE

Raid Alliance Logistics Liaison Equipment is a client-side Fabric mod for
Minecraft 1.21.11. The current foundation includes lifecycle composition, a
RALLE-owned feature and settings registry, local settings persistence, and a
searchable owo-lib settings screen opened with `/ralle settings`. `/ralle lfg`
opens a disconnected owo-lib browser prototype populated with fixed fake lobby
data for layout evaluation. Chat and live Raid LFG behavior are not implemented
yet, and every exposed option defaults off.

## Development

Requirements: Java 21.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
