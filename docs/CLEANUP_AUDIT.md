# Cleanup and refactor audit follow-up

The 2026-09-21 release audit's Cleanup & refactor filter includes findings 11,
14 and 15. This follow-up uses the local generated audit snapshot in
`outputs/performance-audit/dist/audit.json`.

## 11: Empty feature registry — removed

`RalleClient` now composes services directly without creating or sealing an empty
`FeatureRegistry`. The unused `RalleFeature` contract and `RalleContext.features`
field are removed. Source and documentation searches found no consumers or
published compatibility promise for those contracts. Settings and HUD registries
remain active; their shared `IdentifierRules` validation remains in place.

## 14: LFG orchestration — deferred pending prerequisites

The audit explicitly places this larger refactor after profiling and lock-order
cleanup. Those prerequisites have not been established: `LfgJoinController.tick`
still holds its monitor while calling `RaidLfgService.join`, while service-side
completion can acquire the controller monitor through the completion callback.
Splitting these classes now would obscure that unresolved concurrency boundary.

After addressing the lock order and collecting the scenarios in `DIAGNOSTICS.md`,
extract card bindings, mutation/reconciliation lifecycle, and selector/key routing
as separate cohesive changes. Preserve protocol, cancellation, stale-generation,
reconnect and shared creation-feedback behavior. Class size alone is not evidence
of a runtime performance problem.

## 15: Generated review artifacts — archived

Tracked review screenshots, workbooks, inspection output, generated inventory and
the prototype site archive were copied outside the repository before removal.
The local archive is `../RALLE review archive/cleanup-2026-09-21/`; its
`manifest.json` records each original relative path, byte count and SHA-256 hash.
Every copy was verified against its original before removal. The archive also
preserves the generators, notes and patch alongside their outputs for context.
This local archive is not distributed with the mod; Git history retains the
previous tracked versions for other contributors.

The working tree keeps review generators, Markdown notes, the Fox patch and the
`prototypes/settings-navigation` gitlink. Generator prerequisites remain unchanged:
the missing-settings workbook script imports the user's original spreadsheet from
Downloads and requires `@oai/artifact-tool`. New generated review outputs are
ignored. Actual assets, asset generators and license/provenance files stay in place.

The unused root `owo.mixins.json` and `owo.accesswidener` dependency extraction
copies were also archived and removed. Gradle uses `src/main/resources` and bundles
owo-lib as a dependency; RALLE's manifest references its five own mixin lists,
not those root copies. The bundled dependency keeps its own metadata.

These changes simplify bootstrap and checkout/review contents; they do not claim
an FPS or runtime-memory improvement.

## Verification

Java 21 compilation of production and test sources, focused `api.*` tests, and
the full offline Gradle build passed. The full suite reported 503 tests, zero
failures/errors and one skipped test (502 passed).

Release-JAR inspection confirmed all five RALLE mixin lists, bundled owo-lib with
its own mixin/access-widener metadata, and license entries. No removed registry
classes, root extraction files or review artifacts appear in the mod JAR.
In-game behavior and the deferred LFG concurrency/profiling work were not tested
in this cleanup pass.
