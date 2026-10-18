# Class Selector integration

This is an **incomplete integration**, not a working hidden-switch release. The
local settings/editor, persistence, live character-book decoder and discovery,
input guard and bounded controller are implemented. Stable game character identity
resolution, graphical/No View input routing, frame capture and black-to-world
handoff are not wired. Do not enable automation or claim in-game
validation on the strength of the controller tests.

## Settings and local data

Wheels has Class Selector (off), Wheel Style (Circle Segments, Bars, No View),
Class Modifier (Unbound) and a dependency-hidden Class Shortcuts panel. None
depends on Raid LFG, guild access, cosmetic access or the LFG service. Style and
modifier preferences persist as `wheels.class-selector-style` and
`wheels.class-modifier-keybind`; the toggle is `wheels.class-selector-enabled`.
The modifier is currently a saved preference; registration/synchronization with
Minecraft controls belongs with the pending runtime input adapter.

`config/ralle-class-shortcuts.json` version 1 separates immutable character and
ordered shortcut lists by Minecraft account UUID. There are initially no selected
shortcuts. Each shortcut saves the game identity, local name override and keyboard
binding (`unbound` initially). Names use local override, then game nickname, then
class type. Characters missing from a complete scan become unavailable without
discarding their shortcuts, names, keys or order. Partial scans merge; they never
erase. Reappearing stable identities recover their existing shortcut preferences.
Display names/types are never identities. Removing a shortcut does not remove a
discovered character or issue a server action. Writes are atomic and publish only
after persistence succeeds. Invalid or oversized files remain untouched and the
editor fails closed, without disabling chat or LFG.

The class and consumable editors use `SettingsListEditorColumns`: the same
220-pixel-high scrolling list, 104-pixel right action column, framed navy surfaces,
spacing and scroll behavior. Class actions are Add Class and confirmed Reset,
without import/export. Reset removes shortcuts, retaining detected characters.
Empty-state copy is centered in the list; detection feedback stays in the action
column rather than a footer. The compact edit modal offers a local name and option
key with a clear X. The picker uses character numbers to distinguish repeated
names/types, without presenting internal hashes as game identities.
Until verified resource-pack sprite resolution is implemented, native items
provide local fallback icons. Conflicting option keys are visibly marked; the
guard treats duplicate options, the modifier itself and other RALLE action
bindings as conflicts.

## Verified visual evidence and capture

The user-provided `2026-10-18 23-49-44.mp4` shows `/class` transitioning through
black to the world-rendered character-selection stage. Its action bar says
`Left-Click to play` and `Right-Click to switch`. **Right-click** opens the
`Select Your Character` book. Left-clicking an existing character closes it;
the server then runs the selection animation and goes black before revealing the
destination world. This corrects the initial plan's left-click at the first stage.
The user confirms the right-click can happen sooner than in the video. Use the
observed readiness signal rather than a fixed delay. The recording establishes
the visual sequence, not the underlying identity or transition packets.

The user's subsequent local game captures contain four existing characters:
Archer, Mage, Shaman and Warrior. The raw menu title is U+CFFD5 followed by U+E01F.
`WynncraftClassMenu` recognizes that title plus four independent utility-control
name/model signatures and decodes the fifteen character positions. It requires
the select/edit actions and the Class, Time Played, Location and Level fields for
each existing entry. Create/locked/utility entries never become characters.
Missing or unrecognized character positions make a scan partial. One captured
update is missing the Mage position: it must not erase the previous Mage entry.
Housing and other unrelated menus fail recognition.

**Those captured items carry no stable character IDs.** The decoder therefore
creates explicit `observed:<slot>:<sha256>` local references from the full visible
evidence, including position, model, name and lore. These support discovery and
editor curation only. They are never treated as verified server identities, and
the switching controller rejects them even if a caller claims a verified adapter.
Changed evidence produces a new reference; the system does not guess that it is
the same character. Old shortcut preferences remain saved but can be unavailable
until a stable identity adapter is available. A nickname or class type alone never
merges entries. The decoder removes legacy formatting and resource-font glyphs
from displayed names/lore while preserving ordinary text.

`ClassMenuScanner` now connects live received menu contents to `ClassDiscovery`
while enabled on Wynncraft. It uses the original open-screen packet title so
another mod replacing the visual screen does not hide the data. Enabling with a
recognized current menu also scans available contents. The source never opens
`/class`, clicks a menu or performs a web lookup. On the first upgrade, an account
with no saved discovery record recovers matching local capture files (at most
four, bounded to 256 KiB each), through the same decoder, as partial historical
evidence. Existing saved accounts win. Recovery accepts only the current account
and cannot clear entries. The earlier capture attempts are therefore useful
without requiring the player to repeat them.

To gather the missing data with this development build:

1. Enable Class Selector in Wheels.
2. Naturally encounter character selection (login or manually run `/class`),
   right-click to open the book and briefly hover existing character entries.
3. Share the generated `config/ralle-class-menu-capture/class-menu-<account>-*.json`.
   Include two naturally encountered menus if possible, so stable identity can
   be compared across selection/reopening. Do not send launcher/session files.

The temporary capture bridge is inert while disabled or off Wynncraft. It
recognizes the exact pair of original server action-bar instructions before
observing the exact character-book title, excludes player inventory slots and captures serialized
received items/title/slots locally. It sends no commands/clicks and makes no HTTP
requests. It writes at most four snapshots per connection/account, each at most
256 KiB, and stops on an I/O error. It observes only within 15 seconds of the
selection prompt, resets on disconnect/account changes/disable, and never uses
the data as permission to click. Remove this temporary bridge after implementing
and verifying the standalone adapter. A video alone cannot establish stable IDs.

## Domain controller contract

`ClassDiscovery` rejects scans when disabled, from another account, or from an old
connection/enable generation. It never triggers `/class` or a web lookup. Only a
verified game decoder may declare a scan complete or mark the current identity.

`ClassSwitchController` advances from idle to waiting for stage, waiting for menu,
and waiting for transition. Submission requires a verified adapter and a target
different from the known current identity. Its actions contract captures the
frame, sends `/class` once, right-clicks once at readiness, and left-clicks the
uniquely resolved live target once. Partial menus cannot select. Missing or
duplicate identities, duplicate slots and unexpected menus fail closed. Repeated
observations and simultaneous submissions cannot dispatch again. Releasing the
modifier after submission must not call cancel. Escape explicitly cancels
remaining actions, without attempting to reverse accepted server actions.

The overall deadline is 15 seconds. Account changes, disconnect, timeout,
unexpected menus, ambiguous targets, action failures, and Escape release the frame
exactly once and provide a failure reason. Success releases only on the adapter's
verified black-to-world signal with the actual target identity. The actions
interface currently has no Minecraft implementation: frame-resource ownership is
tested using a fake, not an actual framebuffer. Keep underlying ticks/networking
and required rendering running when the live compositor is added.

`ClassSelectorInput` stores identity on fresh press. The latest fresh option
press wins; stale releases/repeats do nothing. Releasing the selected key while
holding the modifier submits once. A fresh mouse click and keyboard releases
share one submission guard. Preheld keys, modifier cancellation and Escape cannot
submit. It remains to connect this to the shared Circle Segments/Bars primitives
and No View runtime, including input suppression during switching.

## Validation remaining

Automated tests replay the real captured item components (with the account UUID
replaced by a test UUID), including nested text, font glyphs and models. They
cover complete/partial decoding, utility/create/locked exclusion, unrelated
screens, repeated names/types, renamed observations, malformed evidence,
account rejection, capture recovery and rejection of unverified automation.
Additional tests cover complete/partial reconciliation, repeated names/types,
renames/removals, account and connection generations, restart persistence,
failed writes/corrupt files, order, defaults, dependencies, conflicts, stale keys,
exactly-once stage advancement and cleanup at every controller exit.

Before completing integration, find and verify a stable game identity source,
validate live discovery from login/book data, earliest right-click readiness, complete-menu
signals, target resolution and black-to-world boundary. Then validate real
framebuffer/resource cleanup, Escape at each live stage, timeout, disconnect,
multiple GUI scales, both interface fonts, resource reloads, and standalone plus
Wynntils configurations. The existing automated tests and recording do not
substitute for those checks.
