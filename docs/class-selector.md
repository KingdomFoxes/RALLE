# Class Selector integration

This is an **incomplete integration**, not a working hidden-switch release. The
local settings/editor, persistence, discovery reconciliation boundary, input guard
and bounded controller are implemented. The current server data decoder, live
character identity resolution, graphical/No View input routing, frame capture and
black-to-world handoff are not wired. Do not enable automation or claim in-game
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

The scrolling list and compact in-screen Add/Edit modals use existing RALLE navy
surfaces, typography, buttons, scrolling and list components. There is no
import/export. The edit modal offers a local name and option key with a clear X.
The picker exposes identities in tooltips to distinguish repeated names/types.
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

To gather the missing data with this development build:

1. Enable Class Selector in Wheels.
2. Naturally encounter character selection (login or manually run `/class`),
   right-click to open the book and briefly hover existing character entries.
3. Share the generated `config/ralle-class-menu-capture/class-menu-<account>-*.json`.
   Include two naturally encountered menus if possible, so stable identity can
   be compared across selection/reopening. Do not send launcher/session files.

The temporary capture bridge is inert while disabled or off Wynncraft. It
recognizes the exact pair of original server action-bar instructions before
observing a container, excludes player inventory slots and captures serialized
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

Automated tests cover complete/partial reconciliation, repeated names/types,
renames/removals, account and connection generations, restart persistence,
failed writes/corrupt files, order, defaults, dependencies, conflicts, stale keys,
exactly-once stage advancement and cleanup at every controller exit.

Before completing integration, verify the actual game identity schema and full
login scan from captured data, earliest right-click readiness, complete-menu
signals, target resolution and black-to-world boundary. Then validate real
framebuffer/resource cleanup, Escape at each live stage, timeout, disconnect,
multiple GUI scales, both interface fonts, resource reloads, and standalone plus
Wynntils configurations. The existing automated tests and recording do not
substitute for those checks.
