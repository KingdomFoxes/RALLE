# Quick Win audit follow-up

The local 2026-09-21 audit snapshot lists findings 07, 08, 10 and 11 as Quick Wins.
Finding 11 was completed in the earlier cleanup pass; see `CLEANUP_AUDIT.md`.

- **07 — navigation persistence:** scroll changes update memory immediately and
  coalesce behind a 300 ms idle delay. Page changes and screen removal flush the
  final snapshot. Identical states do not write; failures remain nonfatal. Closing
  during search preserves the underlying page rather than the search scroll.
- **08 — timer and icon allocations:** elapsed text, components and widths are
  cached per displayed second with font/language/resource invalidation. Fractional
  creation times, minute/hour transitions and frozen timeout cards retain their
  existing behavior. HUD raid previews reuse private, enum-bounded item stacks.
- **10 — idle HTTP infrastructure:** both default gateways share one lazy JDK
  client. Construction does not create it or send requests. Per-request credentials,
  timeouts, LFG HTTP/1.1, bounded retries and injected-client ownership are preserved.
  The owned transport is shut down only when the Minecraft client stops.

Focused regression tests exercise continuous scrolling, final flush/reload,
identical snapshots and failed-write retry; timer presentation reuse, invalidation
and fractional seconds; concurrent transport first use, unused shutdown and public
rank/LFG bearer isolation. Existing HTTP and timeout-card tests are also retained.

Validation: the focused suite and full Java 21 offline Gradle build passed. The
full suite reported 512 tests: 511 passed, one skipped, zero failures/errors.

In-game JFR comparisons, actual settings close/reopen and HUD-editor transitions,
GUI-scale/font/resource reload visuals, and live reconnect checks remain release
validation. Automated checks establish the cache/write/lifecycle behavior, not
measured FPS gains or a complete in-game compatibility result.
