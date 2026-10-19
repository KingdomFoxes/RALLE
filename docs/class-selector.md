# Class Selector integration

The native switching adapter is implemented. It retains a GPU copy of the
submitted frame, sends the bounded command/click sequence and observes the
server-confirmed world return while normal rendering and ticks continue. Automated
checks replay the user's menu capture and the door/load sequence, and an
opt-in test exercises the real Minecraft GPU copy/readback APIs. A full live
Wynncraft switch, standalone and with Wynntils, still needs in-game validation;
these checks do not establish a stable server character UUID.

## Settings and local data

Wheels → Class Selector has Class Selector (off), Wheel Style (Circle Segments, Bars, No View),
Class Modifier (Unbound) and a dependency-hidden Class Shortcuts panel. None
depends on Raid LFG, guild access, cosmetic access or the LFG service. Style and
modifier preferences persist as `wheels.class-selector-style` and
`wheels.class-modifier-keybind`; the toggle is `wheels.class-selector-enabled`.
Class Modifier is also registered under RALLE Wheels in Minecraft controls, with
two-way synchronization between vanilla and RALLE settings. First reconciliation
runs after options load. A saved vanilla binding can populate an otherwise
Unbound first load. Unsupported mouse modifiers do not open keyboard selectors.

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
Received class sprites persist as an optional `sprite` with potion model number
and tint in each character record. This is backward-compatible with version 1
files lacking that field. `ClassPresentation` reconstructs the received potion
model/tint for the native Minecraft item renderer, letting the active resource
pack supply its own class-book artwork in lists, the picker and selector. No
external textures are copied or bundled. Item rendering resolves resource reloads
normally; missing evidence falls back to Paper. Matching historical captures may
hydrate missing sprites without resurrecting removed characters or changing
shortcut names, keys or order. Conflicting option keys are visibly marked; the
guard treats duplicate options, the modifier itself and other RALLE action
bindings as conflicts.

## Modifier and presentation

`ClassSelectorBindings` owns keyboard events before ordinary gameplay/screen
callbacks, with tick recovery sharing the same submission guard. While enabled
on Wynncraft, holding the registered modifier opens `ClassSelectorWheelScreen`
for Circle Segments/Bars. It uses the shared selector faces, stable hit geometry,
120 ms reversible five-pixel motion, configured fonts, received class items and
shortcuts in saved order. Bars wrap/scroll using the shared layout and reveal
keyboard-selected rows. Deliberate pointer movement takes over from keyboard
selection; a stationary pointer does not. A left button held before opening is
inert until released and pressed again. The screen is non-pausing, supplies
narration and suppresses gameplay keys while it owns input.

No View opens no screen. It uses the shared action-feedback renderer with its own
state, independently of the LFG service. It explains an empty list or missing
usable option bindings. Option press highlights, and selected-option release
while the modifier remains down submits exactly once. Mouse/keyboard dispatch,
key repeat, preheld keys and tick recovery cannot submit twice. An unavailable
option's release leaves the hold usable. Escape, modifier release, focus loss,
account/connection changes, disabled settings, changed bindings/style or changed
shortcut data cancel an unsubmitted selection and require a fresh modifier hold. Graphical failures remain
visible through release via feedback over gameplay. Class feedback and LFG
feedback cannot overlap, and connection changes clear class feedback.

Class/LFG modifier conflicts are reciprocal: both action owners remain inert.
The Class Modifier control and editor show the conflict; conflicting option keys
also carry a visible warning in the wheel. Ordinary gameplay input passes through
when the feature is disabled or outside Wynncraft. After submission, modifier
release does not cancel switching. Escape exposes the current game state and
cancels remaining automation. No success sound or synthetic world transition is added.

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
The October 19 clarification and screenshot establish the intended sequence:
`/class` once, repeat right-click until this character book opens, then select the
requested class once. Stage retries are capped at one per 100 ms and 150 attempts
inside the existing 15-second operation deadline; neither the command nor the
character selection is retried.

The user's subsequent local game captures contain four existing characters:
Archer, Mage, Shaman and Warrior. The raw menu title is U+CFFD5 followed by U+E01F.
`WynncraftClassMenu` recognizes that title plus four independent utility-control
name/model signatures and decodes the fifteen character positions. It requires
the select/edit actions and the Class, Time Played, Location and Level fields for
each existing entry. Create/locked/utility entries never become characters.
Missing or unrecognized character positions make a scan partial. One captured
update is missing the Mage position: it must not erase the previous Mage entry.
Housing and other unrelated menus fail recognition.

**Those captured items carry no stable character IDs.** The decoder creates
explicit `observed:<slot>:<sha256>` references from the full visible evidence,
including position, model, name and lore. They are not server identities. At
submission, the native adapter requires the same reference in a complete,
recognized **live** book. It also hashes that entry's evidence without position
and requires it to occur exactly once: two otherwise identical entries in
different positions cannot be safely distinguished. It reads and validates the
live menu again immediately before the click. Historical captures, names, types
and slot numbers alone never authorize selection, and there is no nearest-match
or position fallback.

Changed evidence produces a new reference. This includes changes to nickname,
location, level or time played, so a previously played character can require
explicit reassignment in the editor. Preferences remain saved in their original
row; the system never silently transfers them to a potentially different
character. This conservative limitation remains until the game supplies a
verified stable identity source. The decoder removes legacy formatting and
resource-font glyphs from displayed names/lore while preserving ordinary text.

Partial refreshes retire the old catalog observation at an updated position,
preventing duplicate entries without redirecting its shortcut. A stale shortcut's
Edit popup offers `Choose detected class…`: the player explicitly chooses the
replacement, retaining its list position, local name and option key. Duplicate
targets and account changes are rejected. Closing that picker returns to the
original edit popup without applying a reassignment.

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

For future compatibility diagnostics with this development build:

1. Enable Class Selector in Wheels.
2. Naturally encounter character selection (login or manually run `/class`),
   right-click to open the book and briefly hover existing character entries.
3. Share the generated `config/ralle-class-menu-capture/class-menu-<account>-*.json`.
   Include two naturally encountered menus when investigating changed entries.
   Do not send launcher/session files.

The temporary capture bridge is inert while disabled or off Wynncraft. It
recognizes the exact pair of original server action-bar instructions before
observing the exact character-book title, excludes player inventory slots and captures serialized
received items/title/slots locally. It sends no commands/clicks and makes no HTTP
requests. It writes at most four snapshots per connection/account, each at most
256 KiB, and stops on an I/O error. It observes only within 15 seconds of the
selection prompt, resets on disconnect/account changes/disable, and never uses
the data as permission to click. Remove this temporary bridge after implementing
and verifying the live adapter in-game. A video alone cannot establish stable IDs.

## Switching and load confirmation

The current loop is: capture the selected frame, send `/class` once, submit native
Use clicks at most every **100 ms** while the original server selection prompt is
present and the native scene accepts input, stop on the character-book open
packet, and select the target **once** from complete, uniquely resolved live
contents. The entire operation still has a 15-second deadline and at most 150
stage clicks. The first click has no artificial delay; slow ticks cannot queue
catch-up bursts. Auto Open skips stage clicks.

`ClassContentSignalsMixin` observes `handleContainerContent` and
`handleContainerSetSlot` at TAIL, after vanilla has applied the items. It asks
`ClassSwitcher.menuUpdated()` to select immediately, avoiding the previous wait
for END_CLIENT_TICK and its preceding capture/discovery disk writes. The tick
path remains a fallback. Both paths share the same controller state, exact live
book validation and single target-click guard. A partial book waits; missing or
ambiguous complete evidence still aborts. No slot/name/type fallback was added.

`ClassStageInputMixin` dispatches each permitted click inside vanilla's actual
`handleKeybinds`. The current Use binding is honored without leaving it held;
other bindings sharing the same physical key are drained. Vanilla chooses the
live interaction route. RALLE never invents entity IDs, interaction ranges,
use-item packets or commands to skip server stages.

The latest 12:08–12:17 attempts established that right-click and class selection
now work, but every selection immediately ended with `UNEXPECTED_MENU` before
the server's world welcome. This both exposed the door animation and discarded
the operation before it recorded the current class. The earlier blackout
readback could also miss brief initial blackouts. Both sources of visual
inference have been removed from the runtime.

### Freeze ownership and the door animation

`ClassSwitchController` advances through CAPTURING, WAITING_STAGE, WAITING_MENU,
WAITING_TRANSITION (one target click sent), and WAITING_WORLD (book closed).
**Book-close does not release the frame or audio.** All door animation, initial
blackout and final handoff frames remain covered by the selected GPU frame.
There is no screen-darkness check, frame readback, synthetic fade or fixed
animation sleep in the switching path.

`ClassMenuSignalsMixin` observes original packet titles/text at HEAD with priority
1200 on the client thread, before another mod can transform or cancel display.
At container-close it records both the packet ID and the actual live container.
Minecraft 1.21.11's `handleContainerClose` closes the live menu regardless of the
packet ID. A zero-ID close can therefore acknowledge the selected live book,
including when another mod has already locally dismissed it. Initial inventory-0
closes before selection cannot establish a load. Unrelated nonzero closes and
unrelated pre-load open-menu packets still fail. A lingering native/replaced book
screen after selection is allowed to finish its normal close lifecycle.

`ClassLoadSignals` recognizes the naturally received world welcome observed after
every successful selection in the supplied logs: a standalone `Welcome to
Wynncraft!` line accompanied by the server website line. Font glyphs and legacy
formatting do not prevent recognition. This is a load-stage signal, not a
character identity. Completion additionally requires a complete rendered frame
with the camera on the local player, a non-spectator game mode, no screen or
resource overlay, the inventory container active, no busy hands and no current
selection prompt. A detached door camera, spectator stage, stale menu, or welcome
alone cannot finish. These native camera/mode checks still require live
standalone and Wynntils validation on the current server revision.

Wynntils can start passive inventory queries immediately after the welcome.
After the selected book has closed and that fresh welcome arrives, their menus
may open/close normally beneath the frame; RALLE submits no further menu clicks.
The freeze waits for them to finish and for a rendered usable world. Before
those load signals, unrelated menus still abort.

`ClassSwitchFrame` now owns only one GPU texture and native texture copies. It
never replaces the live render target or pauses world/GUI/network ticks.
`ClassSwitchAudio` keeps all output channels muted without changing saved sound
options, audio playback or ticking. Frame, audio and input ownership end together
on confirmed world return. Escape, timeout, disconnect, account change, focus
loss, disable, resource reload, resize, failure and shutdown release ownership
exactly once. Modifier release after submission does not cancel. Escape sends no
undo command or synthetic server-menu close.

### Remembering the loaded class

`ClassLoadTracker` owns current-class knowledge independently of the presentation
operation. Both a verified manual book click and an automated target click record
a pending observation. It becomes current only after book-close, a fresh world
welcome and the rendered gameplay checks above. Cancelling the frozen view after
selection does not erase a later real load. A new selection stage invalidates
outgoing current-class knowledge; disconnect/account changes discard session
confirmation. Duplicate loads do not repeatedly write storage.
Unconfirmed selections and load signals expire after 15 seconds; an abandoned
click cannot be assigned to an unrelated later class load.

`ralle-class-shortcuts.json` remains version 1 and gains an optional account-scoped
`loads` map. Each entry stores `lastReference` and up to 64 `references` associating
a naturally received compass token with a previously observed book selection.
Existing files without `loads` remain valid; shortcut edits, reset and scans
preserve this data. Writes retain the existing atomic save-before-publish policy.
A storage failure cannot crash unrelated features or erase confirmed in-session
knowledge. No credentials, network queries or new user commands are involved.

The naturally received compass in hotbar index 7 (inventory container slot 43)
can carry an eight-character partial character ID in its first lore line. The
current logs show Wynntils reading those IDs at world entry, and its public
CharacterModel documents this received-item field. RALLE only observes incoming
inventory packets; it never opens the compass or calls the public player API.
The token is **not** treated as a full UUID or permission to click a class. It can
recover current-class knowledge on a later natural load only when this account
already recorded that exact token/selection association. A stale outgoing compass
cannot authorize a new association; selection clears it and requires a fresh
inventory update. A late refresh can still be saved after confirmed gameplay.

Conflicting references for a short token disable its recovery; no identity is
guessed from class type, name, position, last-used history or another account.
`lastReference` survives restarts as history, but is not blindly presented as
current on reconnect. A first-time auto-join without a previously observed token
association remains unknown until a real book selection establishes it. This
tracking does not change the strict full-evidence requirement for target clicks
or silently retarget a shortcut whose book evidence changed.

### Animation-skip research

The [official character-selector announcement](https://forums.wynncraft.com/threads/2-1-1-class-select-new-raid-boss-more.319763/)
describes the overview and right-click book flow, but does not document a door
skip. The official [Wynntils feature list](https://modrinth.com/project/wynntils)
lists Auto Skip Cutscenes, but that listing does not establish that the class-entry
door accepts a skip. No supported door-skip mechanism was verified. This change
therefore hides the server-controlled sequence while it completes; it does not
send speculative skip commands, fake control packets or suppress server state.

Current-class field reference: [Wynntils CharacterModel](https://github.com/Wynntils/Wynntils/blob/main/common/src/main/java/com/wynntils/models/character/CharacterModel.java).
Only the received-field contract was used; no external source/assets were copied.

## Verification and remaining integration work

Focused checks cover exact complete live-book resolution, immediate target
selection through the packet-tail bridge, 100 ms click spacing and bounds,
Auto Open, zero-ID/native book closes, cleanup at every controller stage, frame
and audio retention throughout a simulated 0.8-second door sequence, and release
only on confirmed gameplay. Hook tests verify the actual mapped Minecraft packet
and input boundaries and that runtime code never samples screen pixels.

`ClassLoadTrackerTest` covers manual/automatic selection confirmation, late and
stale compass evidence, reconnect/account separation, persisted load history,
short-token collisions, damaged storage, and a real load after presentation
cancellation. Existing menu/discovery/settings/input tests continue to cover
partial scans, stale shortcuts, explicit reassignment and disabled behavior.

`ClassSwitchFrameGpuTest` (`RALLE_GPU_TEST=1`) uses a hidden isolated OpenGL window
and actual Minecraft GPU APIs. It verifies that changing live black, door-color
and world-color frames cannot replace the frozen selection, and checks explicit
release, reopening, resize detection and idempotent cleanup. Test-only pixel
readback and the historical video-mask classifier are not shipped as runtime
switching logic.

Before release, perform complete live switches on Wynncraft, standalone and with
Wynntils. Confirm the welcome/camera/mode boundary covers the entire door, passive
queries finish beneath the frame, normal and Auto Open books select promptly,
and current-class indicators persist after manual/automatic loads and reconnects.
Exercise Escape, delayed/missing signals, timeout, disconnect, account changes,
GUI scales, resource reloads and ambiguous targets. Automated/controller/GPU
checks do not substitute for this server integration pass.
