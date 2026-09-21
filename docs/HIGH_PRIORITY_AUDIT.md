# Before release + high: implementation record

Scope: findings 01–06 in the 2026-09-21 RALLE Release Audit's **Before release + high** filter.
Quick wins and later measurement/refactor proposals are separate work.

- **LFG concurrency:** service, store and join-controller state transitions share
  `LfgStateAccess`. Store changes are immutable; listeners, mutation/synchronization
  future completions and accepted command effects are queued in transition order.
  They run after the outermost state lock is released, with no state lock held
  while waiting for presentation listeners. Concurrent transitions do not wait for
  callback delivery. Callbacks must not block waiting on other queued callbacks.
  Mutation completion is marked under lock before delivery; protocol, generation,
  context, revision and pending-action checks remain. Listener failures are isolated.
  Latch-driven tests overlap rejected Join completion and live store notifications
  with tick, pending-state reads, acknowledgement, cancellation and store removal.
- **Chat projection:** cache wrapped lines by message identity, keep only contributors
  to the retained-line ceiling, and assemble newest-first without front insertion.
  Width/settings changes and explicit/resource refreshes invalidate cached wrapping.
  Timestamp capture, deleted-marker transfer and frozen selections retain their rules.
- **HQ inspection:** lazily sample Wynntils territory data at most once per client
  tick. Idle ticks do not build a graph. Disconnect discards the adapter and route
  caches. Ownership, HQ and route changes become visible on the next inspected tick;
  active attack timers bypass this cache and are queried on every render decision.
- **Dissolve:** lossless dictionary packing reduces one decoded RGBA8 level from
  67,108,864 to 22,347,776 bytes. Frame trajectories, opacity and contributor order
  are unchanged; exact texel lookup adds one shader fetch per logical page.
  `tools/bake_create_dust.py` reconstructs and compares every tile before saving.
  PNG size is not used as a GPU-memory estimate.
- **Player heads:** one access-order cache retains at most 256 pending/resolved
  entries and permits eight outstanding profile/skin resolutions. Failed or absent
  skins retry after 30 seconds. Disconnect clears entries; outstanding work still
  counts toward the limit until completion and cannot repopulate an old session.
  Minecraft's skin manager continues to own downloaded textures.
- **Screenshots:** check dimensions against the smaller of 8192 and the device's
  maximum texture dimension, and cap captures at 4,194,304 pixels before allocating
  GPU/readback storage. Each RGBA surface is at most 16 MiB; this is not a bound on
  total process memory because PNG and platform clipboard copies also exist.
  Only one capture/readback/clipboard operation can be active at a time, even if a
  selection is cancelled. Oversized/busy captures report an actionable local error
  and preserve Preview. Readback flips rows and unpremultiplies directly into one
  RGBA array, removing the intermediate NativeImage and second traversal. Encoding
  and clipboard publication remain on the worker; GPU operations remain on client.

## Automated verification

Final `RALLE_GPU_TEST=1 ./gradlew.bat build --offline` passed on Java 21:
523 tests, zero failures/errors/skips. This includes the hidden-window OpenGL
shader comparison at densities 1 and 2 and the original forward-scatter reference
comparison for all 64 frames. The packed PNG was also unpacked and compared with
the committed original: all 67,108,864 RGBA bytes match. The generated wheel preview
was visually inspected at scales 1.0 and 0.75. `git diff --check` passed.

## Remaining release validation

Run the audit's same-workload recordings before/after with and without supported
Wynntils: chat at 100/500/1500 retention, deletion, scrolling and frozen selections;
roster churn, failed Join, cancellation and reconnect; HQ ownership/routes/timers;
first and repeated wheel hover/playback and resource reload; large screenshots,
readback/allocation and platform clipboard failure. Verify wheel pixels in game at
multiple GUI scales and measure GPU residency/upload stalls and shader cost. Build
and deterministic tests do not establish FPS gains or complete the in-game gate.
