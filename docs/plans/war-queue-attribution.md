# War queue attribution — implementation handoff for Sol

Status: researched implementation plan; no feature code implemented.
Research date: 2026-09-06.
Target: Minecraft 1.21.11, Fabric, Java 21, Wynntils 4.2.7.

## Requested outcome

Extend Wynntils' existing Guild Attack Timer HUD so an observed queue announcement adds the actual Minecraft username before its existing row:

```text
MaxKarson → Detlas (High) 02:31
_Leoh_ → Nemract (Very High) 01:09
Unknown → Ragni (Low) 03:12
```

The local player's username is blue; other usernames are gray. Resolve class nicknames using the real-name hover metadata RALLE already understands. Preserve the territory, defense, timer, current-territory emphasis, sort order, font, shadow, alignment, size, and positioning owned by Wynntils.

This is an explicitly requested local War tool within the existing project scope. It requires no Fox changes, authentication, network requests, commands, keybinds, history storage, or new HUD ownership. It must work with all RALLE chat and LFG settings disabled. Its own setting defaults to false.

## Research findings and evidence

The pinned tag resolves to `55c1ad88d61c18e5cf1d05eed699c02afdc39b29`. The source links below use that immutable revision.

1. [TerritoryDefenseMessageFeature](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/features/TerritoryDefenseMessageFeature.java) supplies the announcement. It remembers territory and defenses from a click in an `Attacking: ...` inventory, then reacts to an ender-dragon growl within two seconds and queues `g %s defense is %s`. The received message body therefore has the shape `{territory} defense is {defense}`. RALLE should observe the received guild message; it should not replicate the inventory/sound mechanism or send anything.
2. [GuildAttackTimerModel](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/models/territories/GuildAttackTimerModel.java) combines server war countdown announcements with scoreboard entries, preferring chat timers when available. It also reads defense announcements, but does not retain their sender. `getAttackTimers()` and `getAttackTimerForTerritory(String)` expose upcoming timers. RALLE can reuse them without parsing the scoreboard.
3. [GuildAttackScoreboardPart](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/models/territories/GuildAttackScoreboardPart.java) reads the Upcoming Attacks segment. Segment removal does not immediately remove model timers. A hidden or vertically truncated scoreboard must not be treated as cancellation by RALLE.
4. [TerritoryAttackTimerOverlay](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/overlays/TerritoryAttackTimerOverlay.java) sorts model timers, creates one `TextRenderTask` per timer, and passes the tasks through Wynntils' renderer. No row-decoration event or template setting is exposed in this class. Its preview uses a separate literal row.
5. [TerritoryAttackTimer](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/models/territories/TerritoryAttackTimer.java) contains only territory name and end time. Its `asString()` applies the original defense colors and current-territory emphasis. Do not globally change this method: other consumers could then receive HUD-only attribution.
6. [ChatMessageEvent.Match](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/handlers/chat/event/ChatMessageEvent.java) is explicitly intended for observers and reaches listeners even when a listener requests hiding the message. [ChatHandler](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/handlers/chat/ChatHandler.java) posts Match before Edit. Prefer this event over observing rendered chat or relying on Fabric events after other mods have canceled/reformatted a message.
7. [WynntilsMod](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/core/WynntilsMod.java) exposes `registerEventListener(Object)` and `unregisterEventListener(Object)`. [RecipientType](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/handlers/chat/type/RecipientType.java) has guild patterns, but the timer model still carries a TODO about relying on that classification. Validate current guild envelopes with fixtures; do not use body-only matching as a fallback for INFO messages.
8. [FontRenderer](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/utils/render/FontRenderer.java) accounts for wrapped text when calculating layout. Prefixing the text before rendering allows its existing sizing and alignment path to account for the extra text.

The compiled, remapped Modrinth artifact `jeBTZ3Zn` was also inspected with `javap -p -c`. Its row factory is exactly:

```text
private TextRenderTask lambda$render$0(TerritoryAttackTimer)
descriptor:
(Lcom/wynntils/models/territories/TerritoryAttackTimer;)Lcom/wynntils/utils/render/TextRenderTask;
```

It invokes `timer.asString()` and constructs a `TextRenderTask` with the overlay's `textRenderSetting`. The disassembly printed successfully, although javap subsequently reported a cache-file access error while closing the archive. Sol should rerun inspection against a readable copy when verifying the finished mixin.

## Player-facing decisions

Confirmed by the request:

- The actual IGN precedes the existing row, separated by ` → `.
- The local player's IGN is blue, all other IGNs gray.
- This modifies Wynntils' existing HUD.

Confirmed follow-up decisions (including the user's correction to the missing-name answer):

- Missing or unresolved attribution displays gray `Unknown → ` before the existing row while the feature is enabled. Do not infer that a scoreboard-only timer was queued by the local player. `Unknown` is a localized presentation fallback, not a stored player identity.
- First observed, valid announcement wins within one continuously active countdown. Repeated announcements by that same player are idempotent. A conflicting later sender does not overwrite it. This means first *observed*, not a claim to know the globally earliest message.

Additional implementation defaults:

- Use Minecraft `BLUE` (`#5555FF`) for self and `GRAY` (`#AAAAAA`) for others. The arrow is gray. Do not recolor the rest of the row or add bold to names.
- Keep full names and let Wynntils wrap at its configured width. Do not silently enlarge saved HUD bounds, shrink its font, or introduce RALLE placement settings.
- Attribution is memory-only and valid only for the current uninterrupted Wynncraft world/guild session. Clear it on disconnect, world/server transfer, character-selection transition, guild/account change, disabling the setting, or adapter failure. A reconnect shows `Unknown` for timers until new announcements arrive.
- A class nickname that cannot be resolved safely produces the `Unknown` fallback. Never make an HTTP lookup just to resolve it.

Document the limitation in useful setting/help copy: this displays the sender of an observed defense announcement. An identical manually typed guild message cannot be distinguished from Wynntils' automatic message. Missing announcements, disabled sender-side announcements, and messages sent before joining cannot be reconstructed. Attribution is presentation metadata, never authority for gameplay actions.

## Implementation slices

### 1. Extract and test reusable guild speaker resolution

Existing code: `src/main/java/org/kingdomfoxes/ralle/chat/rank/GuildRankTitleTransformer.java`, especially `findMatch`, `speakerAfter`, `hoveredRealName`, and `NICKNAME_HOVER`; existing fixtures are in `GuildRankTitleTransformerTest`.

Add a small read-only helper under `chat/identity`, with a result such as `GuildChatMessage(displayName, resolvedIgn, body)`. Keep identity parsing independent of `GuildRankService`, its HTTP gateway, cache, and the rank toggle.

- Work from the original styled `Component` supplied by Match. Preserve the original component for metadata; do not flatten it before resolving the speaker.
- Validate the guild channel envelope and speaker/body boundary before parsing the body. The existing rank transformer accepts rank glyph structures for its own purpose; do not assume that alone proves a guild channel for this new feature.
- Support the current rank-pill envelope, character nicknames with spaces, and verified compact/full guild indicators. Keep format support narrowly fixture-driven.
- For the identified speaker, prefer matching real-name hover metadata over a nickname that merely happens to satisfy the IGN regex. Support straight and curly apostrophes already supported by RALLE.
- Bound traversal (the current helper uses 256 components), text lengths, and nested hover inspection. Match hover metadata to the identified speaker and limit it to the header's effective style; do not accept a hover attached to arbitrary message-body text.
- Accept direct unnicknamed IGNs only from a verified sender span. Reject malformed/contradictory nickname metadata rather than falling back to a plausible-looking alias.
- Validate resolved usernames with `[A-Za-z0-9_]{1,16}`. Compare self with the logged-in account IGN, case-insensitively, not with class display name or guild rank. Prefer UUID comparison if an already available, verified sender UUID is present; no lookup is required.
- Let the rank transformer reuse the extracted hover/identity logic where compatible, preserving its existing presentation and cache lookup semantics. Avoid a broad rank-renderer rewrite.

### 2. Add a pure announcement parser and bounded attribution tracker

Suggested package: `org.kingdomfoxes.ralle.war.queue`.

Suggested responsibilities:

| Type | Responsibility |
| --- | --- |
| `QueueAnnouncementParser` | Validated guild envelope/body and canonical IGN to a territory announcement |
| `QueueAttributionTracker` | Pending observations, active countdown generations, expiry, duplicates, resets |
| `AttackTimerSnapshot` | Immutable territory name/end-time projection from Wynntils |
| `QueueAttributionFormatter` | Safe name/arrow prefix and unchanged original row |
| `QueueAttributionService` | Setting/world gates, lifecycle, tracker coordination, failure containment |
| `WynntilsQueueAttributionIntegration` | Event listener and model projection; all Wynntils references isolated here or in the optional mixin |

These names are suggestions; preserve equivalent separation if fewer classes suffice.

Parser contract:

- Match the whole message body: `{territory} defense is {level}`. Accept only the levels defined by the pinned [GuildResourceValues](https://github.com/Wynntils/Wynntils/blob/55c1ad88d61c18e5cf1d05eed699c02afdc39b29/common/src/main/java/com/wynntils/models/territories/type/GuildResourceValues.java): `None`, `Very Low`, `Low`, `Medium`, `High`, `Very High`.
- Strip presentation formatting through component APIs, not by deleting arbitrary characters from names. No suffix comments, quoted fragments, multiline text, prefix matches, or public/party/private messages.
- Preserve apostrophes, hyphens, spaces, and other legitimate territory punctuation. Compare against the full canonical territory name provided by Wynntils, with narrowly defined whitespace normalization only. Do not fuzzy-match scoreboard abbreviations independently.
- A valid announcement without an active matching timer is pending, not a new timer. Never mutate Wynntils' timer/defense maps.

Tracker contract, using an injected clock and immutable model snapshots:

1. Reconcile snapshots on client ticks before matching pending messages. Never require a particular event-listener ordering between RALLE and Wynntils.
2. If an announcement and active timer already match, bind immediately. If the message comes first, hold it for at most 10 seconds and bind when the matching timer appears. This window is a conservative implementation constant to verify with live captures, not a Wynncraft protocol guarantee.
3. Track active countdown generations by territory continuity, not `TerritoryAttackTimer` object identity or exact end-time equality. Wynntils replaces records during chat refreshes and scoreboard updates; normal drift must not erase the name.
4. On timer absence, expiration, or a verified server capture message, drop attribution for that generation. Retain an old generation's last end time long enough to recognize expiry if a replacement arrives between ticks. Never reuse a completed generation's name without a fresh announcement.
5. Do not treat scoreboard visibility or segment removal as timer absence. Use the model's active timer snapshot. Process capture tombstones at least through the next model reconciliation so listener order cannot immediately resurrect attribution.
6. Exact duplicates are idempotent and do not extend pending expiry indefinitely. Apply the agreed conflict policy. Expired pending messages cannot attach to a much later requeue.
7. Bound memory: proposed caps of 512 pending territories and 512 attributed active territories, evicting expired entries first and then oldest entries. Overflow can lose a prefix; it must never delete a Wynntils timer. Store no chat history or mutable message components after parsing.
8. Clear all state on the session boundaries listed above. Handle a guild identity becoming unavailable conservatively rather than carrying labels into a different guild.

There is no server-supplied attack ID in the inspected timer record. A cancellation/requeue occurring between observations with no lifecycle message and an indistinguishable end time cannot be proven to be a new generation. Document that limitation; do not claim perfect requeue identification. Test distinguishable expiry/absence/capture transitions thoroughly.

### 3. Wire the read-only Wynntils integration

- Reuse the existing exact-version detection in `war/hqdistance/WynntilsCompatibility.java`. If moving it into a shared compatibility package, update the HQ imports and tests mechanically without changing its behavior.
- Lazily create/register the adapter only when the setting is enabled, the compatible mod is initialized, and a Wynncraft world is active. Register once, unregister on disable/shutdown as appropriate, and prevent duplicate listeners after reconnects. Defer registration until the first eligible client tick to avoid Fabric entrypoint-order assumptions.
- Use `WynntilsMod.registerEventListener` with a public `@SubscribeEvent` Match handler. Verify the bundled NeoForge event-bus API is on the compile classpath; if necessary add an exact compile-only dependency matching Wynntils rather than packaging another event bus.
- Observe `event.getMessage().getComponent()` before Edit. Never cancel or rewrite the chat event. Use validated guild-envelope parsing alongside recipient classification; only allow an INFO fallback if the complete current guild envelope independently verifies it.
- Use existing world state and guild identity from Wynntils, including `WorldStateEvent` where useful. No guild-membership polling or backend access.
- Reconcile the model on the client thread. Any deferred work must carry a session generation so it cannot attach to a later connection.
- Contain `RuntimeException` and `LinkageError` at adapter boundaries; clear state, log once, and disable only queue attribution for the client session. Do not swallow or alter unrelated Wynntils failures. Malformed input is a normal rejection, not a session-wide failure.

### 4. Add the HUD-scoped optional mixin

Preferred hook: `@Inject(at = @At("RETURN"))` into the verified `lambda$render$0` method in `com.wynntils.overlays.TerritoryAttackTimerOverlay`, using the full descriptor above, `remap = false`, and `require = 0`.

At RETURN the callback has the timer argument and original `TextRenderTask`. When all feature/runtime gates pass, prepend the resolved name or gray `Unknown` plus the gray arrow to the original task text using `StyledText`/`Component` composition and keep the same `TextRenderSetting`. `TextRenderTask.setText(StyledText)` is available. Build a complete new styled value before setting it so an adapter error leaves the original task intact. Reset prefix styling explicitly before the original row, retaining the original row's own styles. When disabled, unsupported, or failed, return the untouched Wynntils task without even an Unknown prefix.

This hook runs inside the HUD's stream mapping, before Wynntils computes wrapped heights and alignment. No redirect, renderer replacement, global `asString()` modification, or render-context/thread-local flag is needed.

Use a dedicated optional mixin configuration for `war.queue.mixin`, registered in `fabric.mod.json`. The existing `ralle.wynntils.mixins.json` has the package `war.hqdistance.mixin`; do not move those files merely to add this feature. Add `@Pseudo` and a lightweight `IMixinConfigPlugin` that skips the new mixin unless the installed version is supported. The plugin may use Fabric metadata, but must not load Wynntils classes. The runtime bridge must also check initialized/enabled/supported/world/failed state.

Synthetic method names are a version-sensitive compatibility surface. Verify the exact target against both the remapped development jar and production artifact; do not expand the version gate without repeating verification. A zero-match optional injection can fail silently, so include a development smoke check or transformed-class inspection showing the injected call actually exists. A successful Java build alone is insufficient.

Preview: decorate the existing `renderPreview` task with the local account name in blue through a narrowly scoped constructor-argument or render-call argument hook. Keep preview data isolated from the real tracker, and leave it unchanged when disabled/unsupported. It must exercise the same prefix formatter and preserve Wynntils' preview layout. No new RALLE HUD editor entry is needed.

### 5. Settings and documentation

- Add `WAR_QUEUE_ATTRIBUTION_ENABLED_ID` (value `queue-attribution-enabled`) to `RalleSettings` under `War → Attack Timers`.
- Persist `war.queue-attribution-enabled=false` using the existing registry/config path. Missing old config keys inherit false.
- Suggested title: `Show Who Queued`.
- Suggested description: `Shows who announced each queued territory in Wynntils' Guild Attack Timer. Your name is blue; other names are gray. Missing names appear as Unknown. Only announcements received while enabled can be identified.`
- Mark unavailable with a localized explanation when Wynntils is absent or unsupported, following `RalleClient`'s HQ distance pattern. A saved true value cannot bypass runtime gating.
- Explain how to enable Wynntils' own timer overlay and that senders must supply defense announcements; do not automatically change Wynntils settings.
- Add behavior, data lifetime, exact compatibility, and attribution limitations near the War section in `docs/ARCHITECTURE.md`, and a short feature entry in README. Respect concurrent edits to those files.
- Add a short HUD styling rule to the RALLE UI skill when implementing the approved prefix behavior. `AGENTS.md` already allows explicitly approved local War tools; it does not need a detailed feature entry.

## Validation and acceptance criteria

Run focused tests first:

- Identity/parser: real IGN; nickname with spaces; IGN-shaped nickname resolved via hover first; local nickname resolves to blue; wrong/missing/conflicting hover; inherited styles; unrelated body hovers; both apostrophes; current guild indicators and ranks; wrapped styled messages; all six defense values; apostrophes in territory names; anchored rejection of near matches, public/party/private chat, system announcements, formatting injection, excessive component/text sizes.
- Tracker with a fake clock: announcement before/after timer; two territories and two players; duplicate delivery; agreed conflict policy; pending expiry; continuous timer record replacement and end-time drift; scoreboard-to-chat promotion; disappearing/expired/captured timer; same-territory requeue; capture/model event-order reversal; disconnect, guild/world/account changes; rapid disable/re-enable; bounded-memory behavior; no stale deferred work.
- Formatting: exact `IGN → ` prefix, blue self versus gray other, arrow gray, unchanged original styles and task settings, localized gray `Unknown → ` fallback, disabled output identical to original, preview does not alter tracking.
- Regression: guild-rank transformer suite; `RalleSettingsTest`, registry/search/default persistence tests; HQ compatibility tests if shared detection moves.

Then run the supported project test/build commands under Java 21 (for example `./gradlew.bat test build`). Do not add Wynntils as an unconditional test-runtime dependency to make pure tracker tests work. Adapter tests may use an isolated integration configuration if required.

Client verification must cover:

1. No Wynntils installed: startup, settings, chat, and LFG remain usable.
2. Unsupported Wynntils: unavailable setting, no applied queue mixin, no class-loading crash.
3. Supported 4.2.7, setting off: original HUD/chat unchanged; no collection, commands, or requests.
4. Supported 4.2.7, setting on: actual local and remote announcements, including class nicknames; hidden/reformatted chat; HUD disabled and re-enabled; all RALLE chat settings off.
5. Inspect the transformed overlay or Mixin diagnostics to prove the lambda and preview hooks applied. Test the remapped production jar as well as development if mappings differ.
6. Visually inspect a 16-character IGN plus long territory, multiple queued wars, current-territory emphasis, short remaining timers, narrow widths, at least two GUI scales, left/right alignment, and Wynntils editor preview. Confirm the arrow glyph displays with the active resource pack. Preserve native wrapping and ensure rows do not overlap because of incorrect height calculations.
7. Verify late join/missed announcement fallback and that no name survives a distinguishable completed/requeued war or session change.

Do not initiate real wars or send guild messages solely for testing without authorization. Use controlled fixtures first; actual gameplay verification can accompany the user's normal queued wars. Capture structured component fixtures locally so nickname hover metadata is available; ordinary log text is insufficient.

## Workspace notes for Sol

- At planning time, existing uncommitted edits were present in `README.md`, `docs/ARCHITECTURE.md`, `war/hqdistance/HqInspectionService.java`, `assets/ralle/lang/en_us.json`, and `HqInspectionServiceTest.java`. Preserve them and reread current content before editing.
- `gradle.properties` pins Wynntils 4.2.7 / Modrinth `jeBTZ3Zn`; `fabric.mod.json` suggests exactly 4.2.7. However, `run/mods` currently contains `wynntils-4.1.0-fabric+MC-1.21.11.jar`. Use an isolated development run directory with the intended compatible artifact, or explicitly resolve that mismatch before client validation. Do not mistake an unavailable feature on 4.1.0 for a hook failure.
- The shell's default javap is from Java 17. Use the configured Java 21 toolchain for build/client work.
- Planning verified source and compiled hook shape; it did not apply mixins, run gameplay, capture a live queue message, or validate pixels. Those are implementation acceptance steps, not completed checks.

Suggested implementation order: shared identity helper and regressions → pure parser/tracker → optional event/model adapter → HUD/preview mixins → settings/docs → focused tests/build → compatibility and visual client checks.
