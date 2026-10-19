# Class Selector integration

The native switching adapter is implemented. It retains a GPU copy of the
submitted frame, sends the bounded command/click sequence and observes the
underlying black handoff while normal rendering and ticks continue. Automated
checks replay the user's menu capture and video-derived black masks, and an
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

## Domain controller contract

`ClassDiscovery` rejects scans when disabled, from another account, or from an old
connection/enable generation. It never triggers `/class` or a web lookup. Only a
verified game decoder may declare a scan complete or mark the current identity.

`ClassSwitchController` advances through capturing, waiting for stage, waiting
for menu, waiting for transition and waiting for world return. `ClassSwitcher` implements its native
actions. Submission requests capture but sends no command until the next complete
rendered frame includes the selected graphical option. No View clears its local
selection feedback before capture and adds no screen. `ClassSwitchRenderMixin`
runs immediately before vanilla presents its main target, after all world/UI
rendering. It copies that target to one owned GPU texture, closes the live RALLE
wheel behind the retained image, then sends `/class` once. It never cancels or redirects the underlying render or tick loop.

`ClassMenuSignalsMixin` observes original packet titles/text at HEAD with priority
1200, on the client thread, before ordinary mods can cancel the display or replace
a menu. The previous tail/invoke observers could miss a cancelled callback; the
installed Wynntils 4.2.7 metadata confirms cancellable HEAD menu hooks. Only its
hook metadata was inspected for compatibility; no external source/assets are used.
The exact observed action-bar instruction pair is necessary but can arrive during
the initial blackout. `ClassStageReadiness` also requires observing the underlying
black frame followed by a nonblack, usable selection scene. Bright outgoing gameplay
before the blackout cannot satisfy it. No screen/overlay may own input, and the
player/camera must exist with hands no longer busy. This observes readiness rather
than adding a timed sleep.

Once ready, the controller requests one native Use click. `ClassStageInputMixin`
queues it at the head of Minecraft's actual `handleKeybinds`, after rechecking the
account/connection/deadline/focus and the still-present selection prompt.
`ClassStageInput` uses the current Use binding,
leaves its held state false and removes clicks queued on other mappings that share
that binding. Vanilla consumes that single click immediately and performs its
normal target, hand, entity/block and fallback handling. RALLE no longer constructs
a use-item packet or chooses an interaction route. One native click may produce
multiple normal vanilla packets; it is never a retry or another click. Start/end
tick validation, cancellation and Auto Open discard a pending click before it can
be consumed in an incorrect context.

The 02:01/02:02 user attempts confirmed that the earlier implementation saw the
prompt and sent its constructed air-use packet but received no book. This revision
therefore replaces both the premature prompt-only readiness check and that packet
path. It still needs live verification; the observed non-response alone does not
establish which vanilla interaction Wynncraft accepts.

The original open-screen packet must identify the expected book. If Wynncraft's
Auto Open option already supplies it, skip the right-click and resolve that book
directly. Partial contents wait within the existing deadline; a complete book that
cannot uniquely resolve the saved reference aborts. A fresh native inventory
left-click selects the validated slot once. The matching **server** container-close
packet must arrive before any black frame can release the image. Closing inventory
0 while `/class` starts is harmless and cannot arm this handoff.

Only then does `ClassSwitchFrame` downsample the actual underlying render into a
64×36 texture and asynchronously read 9 KiB, with at most one readback in flight.
`ClassTransitionPixels` measures the central half of the image, excluding the
crosshair, to avoid edge HUDs. At least 99.5% of measured pixels must have all RGB
channels at most 8. This threshold rejects the book and bright camera animation
in the supplied recording and recognizes its actual black handoff. Initial
`/class` blackouts are ignored by the controller. The measurement is a visual
handoff signal, not proof of a server character UUID. The frozen frame is copied
back over the live target until the accepted black observation releases it;
vanilla then presents its own black-to-world frames. RALLE never draws a new
black transition. Releasing the image advances to waiting for world return, not
completion. It continues sampling without a frozen overlay until a nonblack live
frame has no menu/overlay, the inventory container is active and the camera is
back on the local player.

`ClassSwitchAudio` acquires an output-mute lease at submission. A narrow
`Channel.setVolume(float)` filter forces only the final Minecraft OpenAL channel
gain to zero while held. Existing channels are refreshed immediately through
`SoundManager.refreshCategoryVolume(MASTER)`; new channels and subsequent volume
updates use the same gate. Music and other sounds keep their normal playback and
tick state. Saved options, category gains and other applications are untouched.
The image is released at native black while audio remains muted; world return
completes the operation and refreshes the channels at their current normal gains.
Every cancellation/failure/disconnect/shutdown path restores the gate exactly once.
The audio thread reads an atomic lease count and never reads/mutates game state.

All raw keyboard, text, mouse buttons, scrolling and camera movement are blocked
during the operation, except Escape. Held key states and queued clicks are
drained. The server book continues ticking and receiving packets beneath the
frozen frame. LFG physical modifier polling cannot open another selector, and
submission is refused while automatic raid requeue is running. There is no LFG
eligibility or service dependency.

The overall deadline is 15 seconds, checked both in ticks and before presentation.
Account changes, disconnect, timeout, unexpected menus, ambiguous targets, action
or GPU failures and Escape release the owned frame and provide concise feedback.
Focus loss, feature disable, a resource-loading overlay or resizing the frozen
frame also cancel. Modifier release after submission does not cancel. No exit sends a retry,
undo command or synthetic menu close. Only RALLE's own selector may be dismissed;
a real server menu remains visible after cancellation. In-flight readbacks retain
their own tiny texture/buffer until their fence completes, and generation tokens
ignore callbacks from cancelled operations. Client shutdown and connection reset
release frame/audio ownership too. Bounded INFO stage logs record the prompt,
right-click, book/container, validated slot, handoff, world return or failure phase.
They omit account IDs, character hashes, raw chat/items and credentials.
`ClassStagePacketMixin` additionally records at most eight outgoing interaction
packet **types** per naturally observed selection scene, labelled automated or
manual. This includes a manual click after Escape so a no-op and a server-unanswered
interaction can be distinguished. It records no payloads, entity IDs, positions or
ordinary gameplay packets and sends nothing. The scope ends on a new server
HUD/menu or disconnect; repeated copies of the same stage prompt do not reset the
budget.

A native manual selection followed by a matching server close establishes the
known current reference; a completed automated handoff updates it too. The wheel
renders that known reference unavailable and rejects redundant submission. The
adapter does not guess the current character from a name when it is unknown.

`ClassSelectorInput` stores the reference on fresh press. The latest fresh option
press wins; stale releases/repeats do nothing. Releasing the selected key while
holding the modifier submits once. A fresh mouse click and keyboard releases
share one guard. Preheld keys and cancellation cannot submit. It is connected to
Circle Segments, Bars and No View. Closing the native/replaced server book does
not mistake modifier release for cancellation of an accepted operation.

## Validation remaining

Automated tests replay the real captured item components (with the account UUID
replaced by a test UUID), including nested text, font glyphs and models. They
cover complete/partial decoding, utility/create/locked exclusion, unrelated
screens, repeated names/types, renamed observations, malformed evidence,
account rejection, capture recovery and exact complete live-book resolution,
including ambiguous duplicates and moved/renamed observations.
Additional regressions cover modifier-hold cancellation/reopening, first-load and
two-way key synchronization, reciprocal action conflicts, unavailable options,
native sprite model/tint preservation, sprite hydration, stale observation
retirement and explicit retargeting without preference/order loss. Settings and
search tests verify the three Wheels subcategories and unchanged persistence.
Additional tests cover complete/partial reconciliation, repeated names/types,
renames/removals, account and connection generations, restart persistence,
failed writes/corrupt files, order, defaults, dependencies, conflicts, stale keys,
exactly-once stage advancement and cleanup at every controller exit.

`ClassTransitionPixelsTest` replays anonymized per-pixel dark masks from the
provided video, covering gameplay, initial blackouts, the book, animation, final
blackout and destination world. It also checks HUD/crosshair exclusion and dark
but nonblack gameplay. `ClassSwitchFrameGpuTest` (`RALLE_GPU_TEST=1`) uses an
invisible isolated OpenGL window and Minecraft's actual GPU API and vanilla blit
shaders. It verifies live/frozen independence, actual texture copy, black/color
readback, cancellation while a readback is pending, reopening, idempotent cleanup
size mismatch and sampling world return after releasing the image. Controller
tests cover the capture-before-command boundary,
exactly-once actions, matching server close, early black rejection, stale callbacks
every stage's cancellation/deadline/disconnect/account exits, Auto Open and audio
restoration after world return. `ClassSwitchReplayTest` combines the real book and
video masks with the actual output-mute gate to check early blackouts, exactly one
selection, native-black reveal and later unmute. `ClassSwitchAudioTest` verifies
existing/new gains, cross-thread visibility, idempotence and failure cleanup.
`ClassSwitchHooksTest` verifies the raw HEAD observer priority, native input handler and audio-channel boundary against actual mapped Minecraft
bytecode. `ClassStageInputTest` uses real `KeyMapping` instances to verify exactly
one consumed Use click, no held repeat, rebound Use controls and shared-binding
isolation. `ClassStageReadinessTest` covers an early prompt during black, bright
outgoing gameplay, replaced prompts, missing/busy scenes and reset; the combined replay uses that
readiness gate too.

Before release, validate the complete live flow on Wynncraft: login discovery,
earliest right-click readiness, native book/close behavior, changed and duplicate
targets, and the visible black-to-world handoff. Include Escape at every live
stage, delayed responses, timeout, disconnect, multiple GUI scales, both interface
fonts, resource reloads, and standalone plus Wynntils configurations. The recording
and isolated GPU/controller tests do not substitute for those integration checks.
