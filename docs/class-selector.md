# Class Switcher integration

The native switching adapter is implemented. It retains the submitted background
and wheel on the GPU, composites growing black ice beneath the sharp menu,
sends the bounded command/click sequence and observes the
1600 ms post-book-close timer while normal rendering and ticks continue. Automated
checks replay the user's menu capture and the door/load sequence, and an
opt-in test exercises the real Minecraft GPU copy/readback APIs. A full live
Wynncraft switch, standalone and with Wynntils, still needs in-game validation;
these checks do not establish a stable server character UUID.

## Settings and local data

Passive book processing uses detached item snapshots of the 54 recognized book
slots. An unchanged owner/title/roster revision and identical item components/counts
skip repeated discovery and Auto Open decoding. In-place item mutations still
invalidate the snapshot, even without a native state-ID change. New menus,
account/connection resets and catalog changes invalidate the relevant cache.
The automated `liveMenu()` path deliberately bypasses this cache and freshly
decodes live items before every target selection.

Diagnostic capture uses the same unchanged-content guard. Item/registry codecs
run on the client thread to build a detached JSON tree of the recognized book;
pretty serialization and atomic file writes run on a separate daemon writer
with at most four queued captures plus one active write. It never retains a live
menu or registry off-thread and never shares the class-identity writer. Existing
four-capture session and 256 KiB file limits remain. Queue/write failures affect
diagnostics only. Shutdown drains that writer with a five-second bound.
`ClassMenuSnapshotTest` covers invalidation and the uncached selection boundary;
`ClassCaptureWriterTest` covers detached persistence and shutdown.

Wheels → Class Switcher has Class Switcher (off), Wheel Style (Circle Segments, Bars, No View),
Class Modifier (Unbound) and a dependency-hidden Class Shortcuts panel. None
depends on Raid LFG, guild access, cosmetic access or the LFG service. Style and
modifier preferences persist as `wheels.class-selector-style` and
`wheels.class-modifier-keybind`; the toggle is `wheels.class-selector-enabled`.
Class Modifier is also registered under RALLE Wheels in Minecraft controls, with
two-way synchronization between vanilla and RALLE settings. First reconciliation
runs after options load. A saved vanilla binding can populate an otherwise
Unbound first load. Unsupported mouse modifiers do not open keyboard selectors.

the `classes` section of `config/ralle.json` version 2 separates immutable character and
ordered shortcut lists by Minecraft account UUID. There are initially no selected
shortcuts. Each shortcut saves the game identity, local name override and keyboard
binding (`unbound` initially). Names use local override, then game nickname, then
class type. Characters missing from a complete scan become unavailable without
discarding their shortcuts, names, keys or order. Partial scans merge; they never
erase. Reappearing stable identities recover their existing shortcut preferences.
Display names/types alone are never identities. Removing a shortcut does not remove a
discovered character or issue a server action. Writes are atomic and publish only
after persistence succeeds. Invalid or oversized files remain untouched and the
editor fails closed, without disabling chat or LFG.

The class and consumable editors use `SettingsListEditorColumns`: the same
220-pixel-high scrolling list, 104-pixel right action column, framed navy surfaces,
spacing and scroll behavior. Class actions are Add class and confirmed Reset,
without import/export. Reset removes shortcuts, retaining detected characters.
Empty-state copy is centered in the list; detection instructions and failure/conflict
feedback stay in the action column, without a detected-class count. Hold left mouse
on a shortcut's item, name or binding to reorder it through owo-lib's
`DraggableContainer`, constrained to ordered list slots. The actual held row follows
the cursor at its original grab point, rendered above the settings screen without
list clipping; a same-size white rectangle with 25% transparency marks the pending position.
The list scrolls near its top/bottom edges. Releasing inside the list persists a
changed order, shared by the selector; releasing outside, losing focus, or changing
account/data cancels the preview. Edit/remove controls keep their normal clicks.
The compact EDIT modal offers a local name and option key without explanatory
footer copy. Saved name overrides are visible immediately on opening, with the
initial caret/scroll at the start of the text. The key button fills Apply's width while unbound; setting a key adds
the clear X on its right and reduces the key button to fit. ADD CLASS choices retain
button hover feedback without character tooltips. Observed shortcuts omit the
live-character-book tooltip; missing-character warnings remain available.
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
shortcuts in saved order. Class circles keep the existing 90-pixel outer radius
and 64-pixel depth for the first ring, with at most seven options per ring.
Additional rings retain that depth with an eight-pixel gap. A lone option on
any ring occupies its upper half only. Class bars have a fixed compact height of
88 logical pixels (55% of the original 160-pixel reference square), for every
option count and name length. Names affect width only. They wrap/scroll using the shared layout
and viewport and reveal keyboard-selected rows, including their bottom outline
and full upward-motion envelope. Entering an available option by pointer or
keyboard plays the shared Vault step cue once per selection change. Class
switching does not play the LFG creation success cues. The current class remains
gray and unavailable; its bottom tag/binding line is omitted in both graphical styles.
Omit the idle click/option-key instruction and hovered class-name line beneath
the graphical selector. Retain names inside options, empty-list guidance and operation feedback.
Deliberate pointer movement takes over from keyboard
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
inside the existing 15-second operation deadline. The October 20 rapid-switch
clarification additionally approves bounded command retries and close/reopen
recovery of stalled character books, as detailed below.

The user's subsequent local game captures contain four existing characters:
Archer, Mage, Shaman and Warrior. The raw menu title is U+CFFD5 followed by U+E01F.
`WynncraftClassMenu` recognizes that title plus four independent utility-control
name/model signatures and decodes the fifteen character positions. It requires
the select/edit actions and the Class, Time Played, Location and Level fields for
each existing entry. Create/locked/utility entries never become characters.
Missing or unrecognized character positions make a scan partial. One captured
update is missing the Mage position: it must not erase the previous Mage entry.
Housing and other unrelated menus fail recognition.

The 22:04 and 22:06 local captures exposed an Auto Open recognition bug: its
disabled button uses model 598, while its enabled button uses model 599. The
decoder originally accepted only 598, so it observed the book-open packet but
rejected the live contents and remained in WAITING_MENU until cancellation or
timeout. Both observed Auto Open models are now accepted with the same exact
item/name/position checks; character identity validation remains unchanged.
The enabled-state capture is retained as
`wynncraft-character-book-auto-open-2026-10-19.json` with its account anonymized.

**Those captured items carry no stable character IDs.** Version 2 stores structured
`identity` evidence (`name` and normalized nonblank lore `details`) separately from
the latest `sprite` model/tint and optional `book` observation (`slot` and the latest
cleaned lore/progression snapshot). New local references use
`observed:<initial-slot>:v3-<birth-hash>`. Birth evidence allocates a reference once;
subsequent matching keeps that reference while updating its book position, progress
and artwork. Existing v1/v2 references remain usable when evidence proves continuity.
These are local observations, not server UUIDs. No identity is inferred from a
class name, a slot number or a nearby match.

`ClassRosterMatcher` groups entries by exact normalized semantic evidence, then
assigns old records to received records one-to-one. It first uses progression
snapshots unique in both rosters, then artwork unique in both rosters. A complete
book with the same group size may assign one remaining old/live pair after all
its peers match; additions, removals and partial updates cannot use this elimination.
This allows an icon and progress to change without invalidating the retained local
reference, and lets distinguishable Mages/Archers follow book reordering. No scoring,
nearest-level, closest-time or position fallback is used. Identical full observations
cannot authorize selection. The adapter requires a complete recognized **live** book
and validates the selected entry again immediately before clicking.
Recognized decreases in play hours or combat level reject continuity, so a newly
created low-level character cannot steal an older character's reference by reusing
its previous icon. A progression reset that contradicts the saved witness requires
explicit reassignment.

Known class reskins are cosmetic transitions in both directions: Mage/Dark Wizard,
Archer/Hunter, Warrior/Knight, Assassin/Ninja and Shaman/Skyseer. Matching canonicalizes
only the exact `- Class:` field and a default title equal to that received class
label. The same class field is canonicalized in progression comparisons. Custom
character names and unknown non-progress fields remain exact. The original semantic
baseline remains stored for compatibility with hash-bearing v2 references, while
the latest received type, nickname and potion artwork refresh normally. Shortcuts,
local name overrides, keybinds, order and loaded-class associations are preserved
through reskins and restarts. Distinct characters in the same family still require
unique continuity evidence; a reskin label alone never disambiguates them. No new
setting, packet, command or saved format is introduced. Automated book replays cover
every pair in both directions, older v2 references, roster reordering and partial
reskin/progression updates. Actual reskin toggling on Wynncraft remains a live check.

Play time, location, level/XP and the recognized Content Progress bar/count are
normalized in semantic evidence and retained separately for continuity comparisons.
Formatting, resource-font glyphs and empty spacer lines
are removed. Models and potion tints refresh on every changed natural observation
without changing a proven local reference. Creating
a neighboring character cannot invalidate an existing entry through changed artwork
or spacer glyphs. Required fields must still be present. Nickname, class family,
gamemode and unknown non-progress lore remain identifying; changes require explicit
reassignment. A book reorder updates the separate position while preserving references,
shortcut order, names, keys, last-load records and compass associations, including
in-session observations and late queued load saves.

Two entries with identical semantic evidence can be distinguished by uniquely
observed progression/artwork. Truly identical full observations receive
`ambiguous-v3-` references and remain unselectable. Those uncertain references are
never silently matched to a later survivor. Removing or renaming a duplicate cannot
redirect an old shortcut; choosing a newly distinguishable entry remains explicit.
Partial books never authorize a target click. No stable server UUID is invented.

The October 23 v2 build incorrectly discarded all progression as matching evidence
and blocked repeated types even when their received observations differed. Its saved
`ambiguous-v2-` references can recover from unique matching artwork and then retain
progress observations normally. The anonymized six-character capture is retained as
`wynncraft-character-book-2026-10-23.json`; replay verifies all six saved references,
including both Mages and both Archers. The original logs did not include submitted
targets, so they could not identify which shortcut produced each rejection. Switch
logs now include the target reference, canonical reference, rejection reason and
bounded roster summary. Reasons distinguish missing continuity from identical
observations. Diagnostic capture now waits for complete books so packet fragments
cannot exhaust its four-snapshot budget.

Version 1 files remain readable and upgrade on the next successful atomic save.
The decoder computes both prior full-item and progress-normalized hashes for
migration proofs. When live artwork has changed, the saved sprite can reconstruct
the previous normalized hash; that entire hash must match, not just name/type/slot.
Historical account-matching captures can also prove an old reference. Migration
preserves row order, local names, keys, last-load records and compass associations;
the next live reconciliation supplies current artwork and structured observations.
If a progress update already replaced the catalog's old hash, migration restores
the proven normalized descriptor only while that position remains occupied by a
saved entry with the same name, type and sprite. A missing/deleted entry is never
restored, and this catalog repair cannot bypass complete live-book validation.
Capture recovery examines at most four bounded files on session initialization,
even when sprites are already present. It does not resurrect removed shortcuts.
No slot/name-only migration is allowed; legacy references without proof still
offer the explicit reassignment action. Historical proof cannot authorize a click
or choose between currently identical observations. Historical evidence may hydrate
an absent `book` observation for a matched existing reference, never overwrite a
newer one, create a removed shortcut, or authorize a target click.

Partial refreshes retain unmatched old witnesses even when a received item occupies
their former position: later packets may move them elsewhere in the same book.
Unproven replacements within an existing semantic group remain provisional until a
complete book arrives, so they cannot poison its next reconciliation. A stale shortcut's
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

1. Enable Class Switcher in Wheels.
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

The **Class Switcher setting itself** requires Wynncraft's **Auto Open** setting
to be enabled. RALLE remembers the last observed Auto Open value for each Minecraft
account across connections and restarts. If it has never been checked, open `/class`
and its character book so RALLE can observe it, even while Class Switcher is off. Unknown or disabled
keeps the toggle gray and effectively off, rejects direct attempts to enable it,
and hides its dependent controls in both settings and search. The disabled row
explains how to enable Auto Open. The screen refreshes when the prerequisite changes.
Saved enabled preferences are preserved but cannot take effect until confirmed;
a fresh install still requires explicitly turning Class Switcher on.

The last observed value is a boolean in an optional account-scoped `autoOpen` map
in the `classes` section of `config/ralle.json` (format version 2, also reading version 1). Older files without the
map remain valid and default to unknown/off. A saved true value satisfies the toggle
gate on the next Wynncraft connection; a newly observed false value locks it again.
Accounts never inherit each other's flag. Turning Class Switcher off does not clear it.
Observations update the current session immediately and use the existing bounded
background writer and atomic file replacement. Unchanged observations do not rewrite
the file or change shortcut revisions; edits, resets, load saves and migrations
preserve the flag. Storage failures retain the current live observation and old file.

While off, only the recognized menu's utility controls are checked and their on/off
flag is saved. No character discovery, diagnostic capture, capture recovery,
commands or clicks run for this check. Saved diagnostic captures cannot unlock the toggle. Manual
class selection remains available with Auto Open off. Shortcut submission retains
its own prerequisite check before sending `/class`, capturing a frame or muting audio.

The initial check uses the already captured `Auto Open` control in slot 53:
potion model 599 means enabled, 598 means disabled. The recognized live book must
still match its existing title and control signatures. The setting is reread from
live contents before automatic target resolution and again immediately before the
click; a disabled observation stops the operation and releases frame/audio/input.
This deliberately basic check can be refined with further live menu evidence;
RALLE never toggles the server setting itself.

The current loop is: send the initial `/class` just before rendering the selected frame,
capture that frame before the next packet-processing pass, submit native
Use clicks at most every **100 ms** while the original server selection prompt is
present and the native scene accepts input, stop on the character-book open
packet, and select the target **once per book** from complete, uniquely resolved live
contents. The entire operation still has a 15-second deadline and at most 150
stage clicks. The first click has no artificial delay; slow ticks cannot queue
catch-up bursts. Auto Open skips stage clicks.

Rapid-switch recovery is local to that same operation and never extends its
15-second deadline, captures a replacement frame, or restarts the ice/audio gate:

During recovery, replace the modifier's existing status text with red, wrapping
`Command failed. Retrying (1/2)` or `Character book stalled. Retrying (1/2)`.
Count retries actually started, excluding the initial attempt; the denominator
comes from that recovery path's budget. Keep the message through that attempt,
update it for the next retry, and restore the ordinary status when the selected
book's close is accepted. A new operation starts without old retry feedback.
The wheel's captured image stays fixed; its status uses a separate transparent
GPU snapshot rebuilt only when the message changes. No View uses the shared
Action Bar presentation above the frozen background. No new setting or sound is
introduced.

- If the command produces no selection-stage/book signal, retry at least 750 ms
  after the previous attempt, with three command attempts total. The native adapter
  must still see usable gameplay: player camera, normal inventory, no screen,
  overlay, spectator mode, busy hands, or pending observed selection. Local send
  failures consume the same attempt budget. Stage/book evidence permanently stops
  command retries for this operation; there is no command retry during a menu or
  after a character click.
- If an announced character book remains incomplete/unrecognized for 600 ms, or
  remains live for 800 ms after its validated target click, close only the
  packet-proven, matching live character book through vanilla `closeContainer`.
  Clear the unacknowledged load observation first. Await a fresh recognized stage
  prompt and reopen through the same native right-click path; Auto Open is also
  accepted. There are at most two book recoveries (three books total).
- A book closed by the server before selection can return to the stage immediately.
  A reopened book must supply complete, unique identifying evidence again; stale,
  missing, changed or ambiguous targets still fail closed. Each book receives at
  most one target click. An accepted selected-book close starts the normal 1600 ms
  release timer and prevents every retry.
- Retain up to sixteen retired book IDs across consecutive operations so closes
  identifying those old books cannot fail a new operation or confirm its load. A fresh book-open packet
  can reuse an old ID. Escape, disconnect, account change, timeout, focus loss and
  resource loading retain their normal cleanup behavior. Retry counters reset for
  each new operation; click spacing and the total click cap do not reset on recovery.
- Before this operation receives/selects a character book, ignore server close
  packets while only the player's inventory (container 0) is live. The October 20
  21:32:42 and 21:39:08 logs show Wynntils world-entry query closes (15 and 9)
  arriving during the next switch's WAITING_STAGE, after those query menus were
  already dismissed locally. They are not character-book closes and must neither
  fail the switch nor acknowledge a selection. Unrelated closes with a live menu
  or after target selection retain the existing validation and failure behavior.

The October 20 local log contains a rapid switch at 20:24:07 that stayed in
WAITING_STAGE until Escape at 20:24:11, and an earlier WAITING_STAGE timeout.
It establishes the missing-stage stall, not a particular server rejection text
or a confirmed mod exception. Recovery therefore uses bounded silence and live
game state rather than guessing unobserved error messages. Successful fast paths
retain immediate command submission and packet-tail target selection. Repeated
live Wynncraft switches with and without Wynntils remain necessary to validate
the chosen recovery windows against actual server delays.

Controller and captured-book replay tests cover capped silent/local-send retries,
the first-stage stop, partial/ignored-click book recovery, immediate reopening
after a premature close, stale contents and retired close packets, container-ID
reuse, account/connection cancellation, unchanged global deadlines and cleanup.
Back-to-back replay operations verify that closing a failed attempt cannot record
it as a loaded character; only the newly validated, acknowledged book is persisted.

`ClassSwitchRenderMixin` starts the command immediately before `GameRenderer.render`
inside Minecraft's `runTick`, after queued packets and client ticks have finished.
This overlaps outbound command delivery with world/GUI drawing and the GPU copy.
The selected wheel remains visible until that copy is ordered; only then is its
live screen dismissed and stage interaction enabled. Minecraft applies response
packets on its next processing pass. Repeated render callbacks cannot duplicate
the initial send; only the bounded recovery path can retry. A capture failure
after the command releases all presentation ownership.
The selected frame and the 1600 ms book-close timer are unchanged. Bytecode checks
verify the supported Minecraft ordering; no off-thread game-state mutation, packet
suppression or speculative earlier interaction is introduced.

With Wynncraft's **Auto Open** enabled in the character book, Wynncraft opens the
book automatically when switching characters after the player has entered a
world at least once (as described by its in-game tooltip). RALLE accepts that
book directly without requiring a selection prompt or a prior right-click. It
does not toggle Wynncraft's option. Once the book opens, even partial contents
stop all stage retries; complete validated contents trigger one target click.
When Auto Open does not supply a book, the existing recognized-prompt clicks
remain the fallback.

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
The first accepted book-close starts a monotonic **1600 ms** animation timer.
At its deadline, the next tick or render callback releases frame, audio and input
together. Duplicate closes, later menus, missing welcome messages, camera state
and spectator state do not delay or restart that timer. No screen-darkness check,
frame readback, synthetic fade or blocking sleep participates in switching.

`ClassMenuSignalsMixin` observes original packet titles/text at HEAD with priority
1200 on the client thread, before another mod can transform or cancel display.
At container-close it records both the packet ID and the actual live container.
Minecraft 1.21.11's `handleContainerClose` closes the live menu regardless of the
packet ID. A zero-ID close can therefore acknowledge the selected live book,
including when another mod has already locally dismissed it. Initial inventory-0
closes before selection cannot establish a load. Unrelated nonzero closes and
unrelated pre-load open-menu packets still fail. A lingering native/replaced book
screen after selection is allowed to finish its normal close lifecycle.

The 12:41–13:01 attempts reached WAITING_WORLD but never observed the expected
welcome signal, leaving the image frozen until Escape. Per the user's correction,
an observed selected-book close plus the 1.6-second timer now completes the switch
without requiring welcome, camera, game-mode or other world-entry heuristics.
`ClassLoadSignals` welcome/compass recovery remains an optional observation path
for known naturally auto-joined classes; it cannot hold the switching freeze.

Wynntils can start passive inventory queries immediately after the welcome.
After the selected book has closed, their menus
may open/close normally beneath the frame; RALLE submits no further menu clicks.
The timer does not wait for those queries to finish. Before the selected book's
close, unrelated menus still abort.

`ClassSwitchFrame` owns the frozen background GPU texture and native texture copies. It
never replaces the live render target or pauses world/GUI/network ticks.
`ClassSwitchAudio` keeps all output channels muted without changing saved sound
options, audio playback or ticking. Frame, audio and input ownership end together
1600 ms after the selected book closes. Escape, timeout, disconnect, account change, focus
loss, disable, resource reload, resize, failure and shutdown release ownership
exactly once. Modifier release after submission does not cancel. Escape sends no
undo command or synthetic server-menu close.

### Black ice and local previews

The retained view composites frozen background/HUD, animated black ice with
a white outer contour, and a transparent frozen menu/wheel foreground, in that
order, without Minecraft's menu blur. `ClassIceCaptureMixin` wraps the native blur
call in `GuiRenderer.draw`: it copies the background after the HUD draw range,
then skips blur for the Class Switcher and its local previews. Other screens
retain the native blur call. `ClassSelectorWheelScreen` keeps that capture
boundary even with blur set to zero.
The submitted wheel's movement clock is pinned at submission. Its menu dimming,
native items, selected labels and geometry are captured once through an isolated
GUI renderer; premultiplied alpha preserves their original appearance. The live
wheel closes only after these captures. No View captures the completed background
without adding a wheel or menu blur. Underlying rendering and ticks continue.

`class_ice.png` is an original, seeded 16-bit arrival field (high byte in red,
low byte in green), generated by `java scripts/GenerateClassIce.java`. Edge-rooted
stems grow angled side branches and finer offshoots. Every stem and offshoot
continues to the viewport boundary rather than stopping at a preset length.
There is no dilation or secondary fill wave: existing branches retain their
width, and increasing density comes from new advancing branches crossing them.
Only the terminal full-coverage frame closes any remaining inter-branch gaps.
The finite viewport and existing release deadline bound the effect; it does not
loop or restart from the roots. Playback uses a logical-resolution
GPU target, a nearest composite and a 16-byte uniform update. Minimum arrival
over each logical cell's footprint preserves thin connected stems at coarser
resolutions. There is no per-frame CPU map generation, texture upload or readback.

`ClassIceTimeline` starts at accepted submission. Until book-close it grows slowly
and approaches a bounded 16% reveal threshold. The first accepted selected-book
close anchors an exponential-in finish from the current threshold, matching the
incoming growth velocity as well as position. The former fixed exponent was
mathematically exponential but dropped speed at book-close before accelerating;
the new exponent is solved once at that boundary to remove that slowdown.
The image reaches full opaque coverage after 1500 ms, leaving the last 100 ms of
the existing 1600 ms interval for presenting the complete effect. Duplicate closes
do not restart it. Release timing, failure handling, audio and input ownership
remain controlled by the existing controller. A stalled render loop can skip that
final presentation window; the switch deadline is never extended to compensate.

`/ralle animteststyle {1,2,3,4}` selects and immediately plays a local preview:

1. The original branches around the screen edges.
2. Branches rooted only at the four window corners.
3. One randomly positioned interior seed, followed by staggered additional seeds,
   each spreading as a diamond (Manhattan distance), without branches.
4. Two main branches rooted at the top-left and bottom-right corners.

All maps use a fixed random seed for repeatable comparisons and are baked by the
same generator; variants use `class_ice_style2.png` through `class_ice_style4.png`.
Style selection is preview-only and lasts for this client session. Real switches
retain style 1. `/ralle animtest 1` and `/ralle animtest 2` replay the selected style
at one and two logical pixels respectively; style previews reuse the last preview
pixel size (initially one). They show a local sample wheel through the
same sharp capture/composite path, simulate book arrival at 800 ms and dismiss at
2400 ms. They require no enabled Class Switcher, server access or class switch,
send no commands/clicks and do not mute audio. Escape dismisses immediately.
Screen replacement, focus loss, resize, GUI-scale change, resource loading,
disconnect and shutdown also clean up the preview.

Automated timeline tests cover immediate/slow book arrival, continuity,
acceleration, duplicate closes, completion and restart. Arrival-map and GPU tests
verify connected growth, one/two-pixel sampling, opaque full coverage and repeated
captures across GUI scales 1, 2 and 4. `RALLE_GPU_TEST=1` exercises the actual
shader in an isolated invisible OpenGL window and writes sample PNGs under
`build/ice-preview`. The sharp background/wheel composition and Wynncraft/Wynntils
switching still require an in-game visual pass before release.

### Remembering the loaded class

`ClassLoadTracker` owns current-class knowledge independently of the presentation
operation. Both a verified manual book click and an automated target click record
a pending observation. It becomes current after book-close and the same 1600 ms
door timer, without waiting for a welcome or camera signal. Cancelling the frozen view after
selection does not erase a later real load. A new selection stage invalidates
outgoing current-class knowledge; disconnect/account changes discard session
confirmation. Duplicate loads do not repeatedly write storage.
Unconfirmed selections and load signals expire after 15 seconds; an abandoned
click cannot be assigned to an unrelated later class load.

The `classes` section of `ralle.json` includes an optional account-scoped
`loads` map. Each entry stores `lastReference` and up to 64 `references` associating
a naturally received compass token with a previously observed book selection.
Existing files without `loads` remain valid; shortcut edits, reset and scans
preserve this data. Writes retain the existing atomic save-before-publish policy.
A storage failure cannot crash unrelated features or erase confirmed in-session
knowledge. No credentials, network queries or new user commands are involved.

Confirmed load records and late token associations now enter a bounded serial
background writer. They still serialize to that JSON file and use atomic replacement;
they are not RAM-only. Current-session knowledge updates immediately, while persisted
snapshots publish only after successful writes. Explicit shortcut edits, scans and
evidence migrations use the same writer in submission order and retain synchronous
error reporting, so they cannot overwrite queued load data or reintroduce a migrated
reference. Reads never wait for storage. Load-only saves do not change the shortcut
revision or cancel an open selector. Shutdown allows up to five seconds to drain
pending writes; failed writes are logged and preserve the previous file. A forced
process termination before an asynchronous write completes can lose that newest
observation. No new setting or command is introduced by background persistence.

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

The October 19 local file contained a last-selected reference but no token
associations, preventing reliable recovery on login. The actual received welcome
contains unassigned resource-font code points such as U+D0059; the old parser only
removed private-use glyphs and rejected this welcome. It now removes unassigned
glyphs too while retaining the exact welcome line and Wynncraft address checks.
`ClassInventorySignalsMixin` observes original inventory packets at client-thread
HEAD with priority 1200, before another mod can transform or cancel their display.
It accepts hotbar index 7 through direct player-inventory packets, inventory-0 slot
43, or a verified player-inventory slot of the currently matching live container.
An identically numbered container-owned slot is not player evidence. Token decoding
uses the exact eight-character lore field from that proven slot, without assuming
the resource-pack item has vanilla's compass base item. Inventory updates before a
selected book closes cannot associate the outgoing character's token with its target.

After one verified load establishes the missing token association, a later login
can recover the saved current character from its fresh inventory token and recognized
welcome, and the existing selector disables that character. Existing last-used-only
records are not silently assigned to the first token encountered; another client
or a manual selection could have changed the actual character. The 1600 ms timer
remains the completion rule for manual/automated book selections, independently of
welcome parsing.

Conflicting references for a short token disable its recovery; no identity is
guessed from class type, name, position, last-used history or another account.
`lastReference` survives restarts as history, but is not blindly presented as
current on reconnect. A first-time auto-join without a previously observed token
association remains unknown until a real book selection establishes it. This
tracking does not bypass normalized identifying-evidence checks for target clicks
or silently retarget a shortcut whose identifying evidence changed.

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
and audio retention throughout a simulated 1.6-second door sequence, and release
at the 1600 ms book-close deadline even without world-entry signals. Tests replay
X-to-Y-to-X with changed progress and verify evidence-proven legacy migration.
Roster regressions cover model/tint refreshes, neighboring-character creation
with changed spacing, book insertion/reordering, preservation of preferences and
tokens across restart, late load writes, old color-dependent hashes, non-progress
identity changes, repeated types with the same artwork but distinct progression,
progress and artwork changing together, and swaps arriving through partial packets.
Removing an indistinguishable duplicate must not resurrect or redirect an old reference.
Hook tests verify the actual mapped Minecraft packet
and input boundaries and that runtime code never samples screen pixels.
`ClassSwitchReplayTest` runs the captured character book through both manual-open
and Auto Open paths: Auto Open sends zero stage clicks, partial contents wait,
complete contents select without another tick, repeated callbacks cannot select
twice, and both paths retain the frame/audio through the book-close timer.
Each path uses its corresponding disabled/enabled captured book, including the
different Auto Open button model. Decoder checks also reject unobserved models,
wrong control names and wrong item types.

`ClassLoadTrackerTest` covers manual/automatic selection confirmation, late and
stale compass evidence, reconnect/account separation, persisted load history,
short-token collisions, damaged storage, and a real load after presentation
cancellation. Existing menu/discovery/settings/input tests continue to cover
partial scans, stale shortcuts, explicit reassignment and disabled behavior.

Additional checks replay restart recovery with the observed welcome glyphs, verify
player-slot provenance and rejection of pre-close outgoing token evidence, and
stall the storage worker while confirming switch completion and reads stay responsive.
They reload the resulting file after account changes, edits and migration to check
ordering and persistence. Pre-render command checks enforce one initial send, no stage
interaction before capture, cleanup between command/capture, and the same 1600 ms
deadline. These checks do not measure a new live switching-time improvement.

`ClassSwitchFrameGpuTest` (`RALLE_GPU_TEST=1`) uses a hidden isolated OpenGL window
and actual Minecraft GPU APIs. It verifies that changing live black, door-color
and world-color frames cannot replace the frozen selection, and checks explicit
release, reopening, resize detection and idempotent cleanup. Test-only pixel
readback and the historical video-mask classifier are not shipped as runtime
switching logic.

Before release, perform complete live switches on Wynncraft, standalone and with
Wynntils. Confirm the 1600 ms book-close timer covers the door, passive
queries finish beneath the frame, normal and Auto Open books select promptly,
and current-class indicators persist after manual/automatic loads and reconnects.
Exercise Escape, delayed/missing signals, timeout, disconnect, account changes,
GUI scales, resource reloads and ambiguous targets. Automated/controller/GPU
checks do not substitute for this server integration pass.
