# Local release diagnostics

`/ralle diag` shows whole-client heap usage, command help, and the three sections
with the greatest inclusive wall time in the last completed recording.

- `/ralle diag start [seconds]`: instrumented section timings and same-thread
  allocation counters. Default 60 seconds; allowed range 1–300.
- `/ralle diag jfr [seconds]`: the same section report plus Java Flight Recorder
  execution/native samples, sampled allocation stacks, GC events and monitor
  contention. This observes the entire JVM, including Minecraft and other mods.
- `/ralle diag stop`: finish early and export.
- `/ralle diag save`: rewrite/retry the last completed JSON report.

Recording is disabled by default, is never persisted as enabled, makes no network
requests, and does not enable any mod features. The idle instrumentation path
returns a shared no-op scope without reading clocks, querying management beans,
allocating per call, or acquiring locks. During recording, scopes allocate and
aggregate under a small lock; measurements include that instrumentation overhead.
Do not interpret these as uninstrumented benchmark results.

Output is local to `<game directory>/ralle-diagnostics/latest.json` and, for an
explicit JFR run, `latest.jfr`. Files are replaced, not accumulated. Copy them
elsewhere between comparison runs. A section-only recording does not replace the
previous JFR file: match its time with the JSON before comparing. JSON writing
and JFR dumping run off the render thread. Export failures retain the JSON report
in memory for `save`; a failed JFR export cannot be retried. An unfinished run is
discarded on client shutdown. Wait for the saved notification before quitting.

The JFR recording has a 64 MiB repository retention target, a duration limit and
an age limit. JFR manages chunks, so this is not a strict byte cap on files or
process memory. Section collection expires independently of client ticks; export
occurs on the next client tick after expiry. There is only one RALLE session or
export at a time. RALLE closes only its own recording and does not change global
thread-allocation tracking settings. If allocation tracking is unavailable or
disabled, `allocationSamples` is zero rather than a claim of zero allocation.

## Interpreting the JSON

- `sections[].calls`, `totalNanos`, `maxNanos`: synchronous **wall** time in each
  named section. Average per call is `totalNanos / calls`; these are not CPU or
  GPU timings. Driver stalls and waiting can contribute. Nested sections overlap:
  never add every row together to claim a total RALLE cost.
- `allocatedBytes`, `allocationSamples`: approximate heap allocation during
  measured calls on their executing thread. This includes called Minecraft and
  library code, and represents allocation churn, not live or retained memory.
  Asynchronous work is measured only where separately instrumented.
- `start` / `end`: heap used/committed/max, non-heap used, direct-buffer memory,
  cumulative GC collection count and collection time for the **whole JVM**.
  Subtract supported GC counters to compare a run; `-1` means unavailable.
- `sampledPeakHeapBytes`: greatest heap usage seen at start, end or a one-second
  sample. Short spikes may fall between samples. Heap deltas include GC and other
  mods and must never be labelled “RALLE memory”.
- Zero calls means a path was not exercised, not that the feature costs nothing.

Instrumented paths: all eleven RALLE end-client-tick services; chat projection,
full-shadow mask preparation, screenshot submission/readback/PNG-and-clipboard;
LFG grid rebuild, notification rendering and selector rendering; HQ inspection
and consumable matching. Render measurements stop at CPU submission and cannot
attribute GPU execution time. Settings rendering, general vanilla chat rendering,
skin fetches, JSON parsing and asynchronous networking need JFR stack inspection.

The lightweight JSON contains aggregate statistics and versions, not chat text,
credentials, server addresses, player identities or rosters. JFR records JVM-wide
stack/class/thread metadata; inspect it before sharing. This custom configuration
does not enable JVM-argument, environment-variable or system-property events.

## Repeatable release experiments

Use the same world/area, window size, GUI scale, resource packs, FPS cap, Java
heap size and mods. Warm the client first. Run each scenario three times for
60 seconds, copying output between runs. Record the enabled settings separately.
Do not generate extra public-server chat or automated command traffic for tests.

1. RALLE installed with every feature disabled: idle and normal gameplay.
2. Chat: timestamps off/on at 100 and 1,500 retained messages; representative
   local message replay, scrolling, hover/click and screenshot selection.
3. Shadows: Vanilla, Full compositor, and compositor fallback where safely
   reproducible. Compare frame times separately using the game's frame graph.
4. Screenshots: short and long selection at low/high GUI scales. Observe native
   and GPU pressure externally; the JSON cannot measure those allocations.
5. LFG: browser open/closed, visible HUD cards, roster updates, three-second Join
   cancellation, reconnect and concurrent updates. Capture JFR for lock stalls.
6. War: hold inspection over a territory; compare key released; queue attribution
   with changing attack timers; consumable inventory with repeated names.
7. Wheels: first hover versus warmed hover, accepted animation, resource reload,
   repeated open/close; compare process/GPU memory before and after.
8. Repeat the important cases with and without the supported Wynntils version.

Open JFR in JDK Mission Control to inspect hot methods, allocation samples and
monitor waits. Filter stacks for `org.kingdomfoxes.ralle` while retaining callees.
JFR samples cannot establish exact retained ownership. A separate heap dump and
dominator analysis can investigate retention, but a heap dump can pause the game
and contain sensitive data; this command deliberately does not produce one.

Acceptance depends on measured frame-time spikes, allocation rate, GC and
long-session retention, alongside unchanged behavior—not an invented universal
FPS score. In-game testing remains required before release.

API references: [JDK 21 allocation counters](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.management/com/sun/management/ThreadMXBean.html)
and [JDK 21 Flight Recorder](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/Recording.html).
