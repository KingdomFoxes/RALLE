# Raid Alliance Logistics Liaison Equipment (RALLE)

A client-side Wynncraft quality-of-life mod for Kingdom of Foxes and allied
guilds. Built for Minecraft Java 1.21.11, Fabric and Java 21. Licensed under
GNU GPL v3.0; bundled third-party assets retain their included licenses.

## Features

- Configurable chat appearance, timestamps, history, channel input and transparent screenshots.
- Raid LFG with synchronized listings, notifications and bounded Wynncraft party actions.
- War tools for HQ distance, queue attribution and consumable highlights.
- Configurable party-create, party-kick and local Class Switcher wheels.
- Fox-authorized supporter nameplate styles.

Local chat works without Wynntils. Wynncraft integrations run only on Wynncraft;
the supported Wynntils guild map and attack timer integrations require Wynntils
4.2.7. Fox-exclusive settings require a current login membership check, and Fox
independently authorizes every LFG action. Class Switcher is disabled by default
and requires an observed enabled Wynncraft Auto Open setting.

Use `/ralle settings`, `/ralle hud` and `/ralle lfg` to open the mod's interfaces.
All RALLE local settings and saved feature state share `config/ralle.json`.
Existing separate files migrate automatically; see [local state](docs/local-state.md).

## Development

With Java 21 selected:

```text
./gradlew test
./gradlew build
```

On Windows, use `gradlew.bat`. Release tooling checks use
`python -m unittest discover -s scripts -p test_release.py`.

See [release automation](docs/releases.md), [wheel behavior](docs/wheels.md) and
[Class Switcher integration and validation](docs/class-selector.md). In-game
validation on Wynncraft, both with and without Wynntils, is required before release.
