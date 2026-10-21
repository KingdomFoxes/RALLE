# Modifier wheels

Class Selector is an independent addition under Wheels with local shortcuts,
game-menu discovery and a bounded hidden-switch runtime. See
[Class Selector integration](class-selector.md) for persistence, strict live-book
target resolution, the native right-click sequence, frozen-frame handoff and
remaining live validation. Its option keys are configurable per shortcut; the
fixed option keys described below apply to Create/Kick.

Circle faces are rasterized once per screen layout into tightly cropped dynamic
textures containing both normal and selected colors. Playback submits one
nearest-filtered image per segment, with the same pixel geometry, transparency,
hit regions and independent motion. The screen retains every visible face
(including all 64 supported class shortcuts), avoiding the old 32-entry cache
thrashing and hundreds of row rectangles per segment per frame. Layout rebuilds,
palette changes and screen removal release the owned textures. Native items and
text still render normally; class name wrapping and option-conflict labels are
prepared with the layout instead of recomputed for each frame. Bars retain their
two inexpensive rectangles per face.

Wheels contains three subcategories: Lfg Create Selector, Party/Lfg Kick Selector,
and Class Selector. Each contains that selector's toggle and style; Class Selector
also contains its modifier and shortcut editor. The Create and Kick wheels remain disabled
by default. Each enabled selector reveals its own Wheel Style: Circle Segments
(default), Bars, or No View. Modifier bindings remain in Raid LFG Controls and
stay synchronized with Minecraft's keybind options. Create/Kick wheel settings retain the
existing dependency on Enable Raid LFG and its current-account Fox access gate.
The toggles are labeled `Lfg Create Selector` and `Party/Lfg Kick Selector`.
The Create selector omits its idle instruction line; processing, success, and
failure feedback still appear in its status area.

The existing toggle IDs are unchanged. Their persisted keys move from
`raid-lfg.raid-lfg-{create,kick}-selector-wheel` to
`wheels.raid-lfg-{create,kick}-selector-wheel`. The loader uses the old key only
when the new key is absent; subsequent saves write current keys. A present but
invalid new value uses the normal default, never the old value. Styles persist as
`wheels.raid-lfg-{create,kick}-wheel-style`, with values `circle-segments`, `bars`,
and `no-view`. Disabled selectors preserve their hidden style preferences.
Moving controls into subcategories does not add subcategory IDs to persisted keys;
all current `wheels.*` keys and legacy-key migration behavior remain unchanged.

## Input

All styles accept the configured keyboard modifier and existing fixed top-row
option keys: Create 1–6 selects Dailies, NOTG, NOL, TCC, TNA, TWP; Kick 2–4
selects the corresponding synchronized roster slot. Press selects; release of
that digit submits only while the modifier is still held. Pressing a different
option while an older key is held replaces the selection. Releasing the older
key does nothing; releasing the latest selected key submits exactly once. Key
repeat never submits. A modifier rebound
to an option digit only opens its selector on that event; it cannot also select
itself. Unbound controls and conflicts between RALLE action bindings stay inert.
These remain keyboard bindings; this feature does not add mouse-button modifiers
or configurable option digits.

Circle Segments and Bars also accept deliberate pointer movement and a fresh
left-click. Keyboard highlighting is retained while the pointer is stationary.
Opening at the centered cursor position does not immediately select a bar.
Mouse and keyboard share one submission guard; a successful mouse submission
discards an armed keyboard chord so a late release cannot dispatch again.
Create submits at most once per hold. No View and disabled-wheel keyboard chords
complete once per hold. Graphical Kick accepts another fresh option press/release
or mouse click after failure or its accepted animation. Releasing an empty or
unavailable option leaves the graphical menu usable without closing it.
Only one Kick request may be pending. Kick targets are UUIDs captured on press,
never a new occupant of a roster index on release.

Releasing the modifier or Escape cancels an unsubmitted graphical selector.
Create retains its existing-party confirmation/submission presentation exception.
Focus loss, changed bindings, unavailable LFG access, and loss of the hosted
lobby cancel the appropriate selection. Closing while a modifier is held requires
physical release before another selector can open. No View and disabled wheels
use the existing gameplay keyboard/Action Bar path, feedback, and sounds; they
never open an invisible selector screen. Normal existing-party confirmation can
still open when required.

## Bars

Bars are side-by-side vertical slices using an original reference square of
side S. Bar height is 55% of S (a 45% reduction); width and wrapping retain the
original reference square. Up to five options divide its width equally.
Above five, each bar is at least 20% of the
original square width (S/5), with pixel rounding. Rows contain at most five
options, wrapping below with each row centered and an eight-pixel gap. Fifteen
options form three rows of five. Rows stay within 64% of the GUI-scaled viewport width. Shared borders
are included in column widths. S is content-aware for Create and smaller for
Kick, clamped to the width cap and aligned to logical pixels. Kick roster changes
recompute the layout without retaining a stale hovered target.

Selection moves a complete bar and its content upward by five logical pixels
over the existing 120 ms cubic ease-out. Hit regions include that movement and
do not follow animation. Short viewports scroll the bar area instead of scaling
away its minimum width; keyboard selection reveals its row. Create, Kick and
Class use the same viewport calculations, including the upward-motion envelope
and complete bottom outline for every wrapped row. Class bars use a fixed compact
88-logical-pixel height regardless of option count or name length. Status remains below
the visible bar area. Resolution and GUI-scale changes recompute geometry.

Both graphical styles retain Create's confirmation, failure feedback, cached
whole-selection dissolve (including item and chosen-font label), and exactly-once
success cues. Rectangular capture bounds include dust margins. Kick explosions
retain their submitted screen position and scale despite subsequent roster
reflow. Player heads reuse the shared cached cosmetic renderer.

## Validation

Focused tests cover settings migration and precedence, hidden-style persistence,
search visibility, mode routing, chord cancellation and duplicate-input guards,
bar division/cap/wrap boundaries, stable hit regions, and unchanged circle hit
geometry. The opt-in `RALLE_GPU_TEST=1` shader test exercises square, tall, and
wide captures at two pixel densities against the independent dust reference.

Before release, check both fonts and several GUI scales in-game, rebound and
conflicting modifiers, simultaneous mouse/key input, live Kick roster changes,
Create import confirmation, focus loss, and reconnects. Include Wynntils-enabled
and standalone configurations. Geometry previews and shader tests do not replace
these integration checks.
