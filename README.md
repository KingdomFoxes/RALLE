# RALLE

Raid Alliance Logistics Liaison Equipment is a client-side Fabric mod for
Minecraft 1.21.11. This repository currently contains the buildable foundation
only: lifecycle composition, RALLE-owned feature and settings registries, and a
minimal owo-lib settings screen. No chat or Raid LFG features are registered.

## Development

Requirements: Java 21.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
