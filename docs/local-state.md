# Local settings and state

RALLE uses one active local file, `config/ralle.json`, with `schemaVersion: 1`.
`LocalStateFile` owns persistence; feature classes own validation and behavior in
their named sections. Settings retain their existing `category.setting` keys and
string values. Runtime access checks still mask locked preferences and reject
unauthorized changes. The file stores no authentication credentials or tokens.

| Section | Contents | Former file |
| --- | --- | --- |
| `settings` | Feature toggles, choices, colors and keybind preferences | `ralle.properties` |
| `hud` | Normalized HUD positions, sizes and anchors | `ralle-hud-layout.properties` |
| `navigation` | Settings page, subsection and scroll position | `ralle-settings-ui.properties` |
| `onboarding` | Pending/delivered installation notice | `ralle-onboarding.properties` |
| `requeue` | Last locally observed raid type | `ralle-auto-requeue.properties` |
| `classes` | Account-scoped characters, shortcuts, load associations and Auto Open observations | `ralle-class-shortcuts.json` |
| `consumables` | Ordered consumable highlight rules | `ralle-consumable-highlights.json` |
| `guildRanks` | Local cosmetic IGN/title cache and fetch time | `ralle-ranks.json` |

The nested `classes` and `consumables` objects retain their own version fields and
validation. These are data-schema versions, not separate active files. Consumable
import/export continues to use the standalone consumable-rule JSON format, not a
complete copy of the player's local configuration. Minecraft's native `options.txt`
continues to own the vanilla side of synchronized keybinds.

## Migration and recovery

When `ralle.json` is absent, existing former files are imported together. Their
contents are copied into the corresponding sections before any new section is
saved. Existing unified files always take precedence: editing an old file does
not override already migrated preferences. Original files are left untouched as
recovery copies; after checking the new file and its behavior in-game, they may
be removed. Fresh installations create only `ralle.json`.

An unreadable former file is recorded under `migrationFailures`. Only that
section becomes unavailable; unrelated sections can still load and save. Repair
the named original file and restart to retry its import automatically. A
malformed, oversized or unsupported-version unified file is preserved and made
read-only rather than replaced with defaults. Repair or restore it while the
game is closed. Valid unknown root sections survive updates.

One background writer prevents the asynchronous class writer, rank-cache refresh
and UI changes from overwriting each other's sections. Routine feature preferences,
HUD edits and navigation saves enqueue detached property snapshots without
waiting for disk. Pending changes coalesce to one latest value and completion
future per section (at most eight pending sections plus one active batch).
Unchanged sections skip writes. Reads use the last durably published immutable
snapshot and never take a lock held during disk I/O. Writes use a unique temporary file in the destination directory,
flush its contents, and atomically replace the destination before publishing
the new shared snapshot. A failed write removes the temporary file and retains
the previous shared snapshot. Filesystems without atomic replacement fail the
save safely instead of truncating the existing file. The unified file is bounded
to 8 MiB; existing class-data and import validation limits remain in place.

Explicit class/consumable edits, onboarding and cache writes retain synchronous
durability/error reporting; their section writes join the same ordered writer.
Completion of a coalesced preference save confirms that its latest replacement
was saved, rather than promising every intermediate slider/scroll position was
written. Async failures are logged; navigation remains dirty and retries after
its existing idle backoff. At client shutdown, class observations flush first,
then the unified file drains accepted updates with a five-second shutdown wait
and rejects new saves. The daemon writer expires its thread after one idle
second. There is no extra save thread for each feature.

The onboarding section is created before installation defaults, so restarting
before the first Wynncraft connection retains the pending notice. Navigation
retains its 300 ms idle debounce and close-time enqueue. Class load observations
retain their bounded off-thread writer and save-before-publish behavior.

Class-menu diagnostic captures and logs are integration evidence, separate from
settings/state. Existing captures still support explicitly verified legacy
reference migration; they never grant Auto Open access or authorize a switch.

`LocalStateFileTest` covers migration, restart persistence across the live stores,
concurrent section updates, corrupt/future schemas, isolated migration failures,
recovery and failed atomic writes. `LocalStateWriterTest` gates a simulated slow
disk behind a 900 KiB payload, verifies 400 UI changes coalesce, checks concurrent
reads, I/O recovery and shutdown durability. These are deterministic concurrency
checks, not an in-game frame-time benchmark. Standalone `Path` store constructors remain
available for isolated persistence tests and data tooling; the client wires all
eight stores through sections of one `LocalStateFile`.
