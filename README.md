# RALLE

Raid Alliance Logistics Liaison Equipment is a client-side Fabric mod for
Minecraft 1.21.11. The current foundation includes lifecycle composition, a
RALLE-owned feature and settings registry, local settings persistence, and a
searchable owo-lib settings screen opened with `/ralle settings`. Its chat
section includes a dedicated editor for moving, resizing, and restoring the
single chat box; placements are normalized across resolution and GUI-scale
changes and apply in singleplayer or on any server. Message direction,
horizontal text alignment, and text-shadow modes are implemented as
independently disabled local options. Transparent chat screenshot selection also supports an optional
per-line text-snapped overlay and text-width image crop, plus processed-xylophone
count and successful-copy feedback. Screenshotting and its sounds are disabled
by default on new installs.
OW-like Chat Tabbing is another disabled-by-default local input option. In an empty
chat, unmodified Tab cycles `[Guild]`, `[Party]`, the latest outgoing `/msg`
recipient or incoming Wynncraft DM sender as `[username]` when known, and `[All]`.
These labels sit outside the
editable message: Backspace, Delete, selection, and cut cannot remove them.
Sending restores the corresponding command prefix without transmitting the label.
Typing a slash command retains command handling; message drafts retain vanilla
Tab completion. New empty chat screens restore the last selected type. That type
and the latest valid recipient reset when disconnecting and are never saved to disk.
On Wynncraft, recognized text-entry prompts automatically select All, including
trade-market search/quantity/price, friend recruitment, and nickname entry.
Input-menu actions also cover Add Ally and pet naming when followed by the
matching server menu close. Draft text is retained; All stays selected until
you change channel. This works independently of Wynntils and observes prompts
before chat-display filtering. It never submits a reply automatically.
Persistent Chat is another disabled-by-default option. When enabled, it keeps
up to the selected 300, 500, 1000, or 1500 displayed messages and wrapped lines
while moving between multiplayer servers and singleplayer worlds. This history
exists only in memory, may mix messages from different worlds without labels,
and disappears when Minecraft closes. RALLE never reloads it from logs or
writes it to another file, and manual F3+D clearing remains permanent.
Guild Rank Style is a local Wynncraft guild-chat choice with unchanged title
pills as its default. It can replace the six standard ranks with zero to five
classic stars, or render those stars and the current title inside one continuous
cyan pill. Internal Guild Ranks remains independent: when enabled, the combined
style keeps the stars from the player's Wynncraft guild rank and uses the
resolved Fox title for the title portion.
While rank customization is active, hovering a retained rank pill shows the
uppercase Wynncraft guild rank and, when available, the member's fully
disambiguated Fox rank group.
`/ralle hud` opens the layout editor with every currently enabled editable HUD
element available; the About page exposes the same action as `Edit HUDs`.
`/ralle lfg` opens the live, authenticated owo-lib Raid LFG browser when the
feature is enabled on Wynncraft. Hosts can disband, kick with a
hold-to-confirm roster interaction, lock or reopen joining, and ping current
party members. Eight additional action shortcuts cover Join, Close,
Leave/Disband, Party Filled, Ping, Lock/Unlock, Create, and Kick; Create and Kick use
top-row-number chords and report their keybind-only feedback through a fixed
Action Bar above the crosshair. Notification cards use a red top-right X for
presentation-only dismissal, including during a Join countdown, and show usable
bound keys beside their Join, Leave/Disband, and Party Filled actions. Action Bar feedback
pairs Vanilla-font recognition glyphs with the selected interface font,
including a remaining-time message when Ping is on cooldown, while rapid
Lock/Unlock toggles are coalesced for one second before any backend mutation.
Automatic Raid Requeue is a separate, unbound-by-default Wynncraft shortcut. While bound, it
remembers the fixed raid name from the most recent server Ready Up chat prompt, whoever initiated the queue
in `config/ralle-auto-requeue.properties`. Pressing it runs one bounded, headless `/pf` flow,
selects that raid from the main Party Finder page or its Party Queue fallback, and clicks Ready Up
while ordinary movement remains available. It does not loop or keep a menu visible.
On new installs, Raid LFG, its notifications, its sounds, and all ten LFG and
Automatic Raid Requeue controls default to disabled or Unbound.
Saved settings remain authoritative for existing users.

The War → Consumables settings page includes a disabled-by-default Wynncraft-only
container-slot highlighter. It matches complete words or phrases in displayed
item names against an ordered local rule list only after an exact Wynncraft
Potion, Food, or Scroll tooltip marker is present. It never matches lore text or
assumes the vanilla backing item proves the type. It supports solid and animated
rainbow one-pixel borders and updates open containers immediately.
The versioned list is stored in `config/ralle-consumable-highlights.json` and can
be imported, exported, edited, emptied, or reset independently of the master
toggle. Its editor is shown only while the master toggle is enabled.
The complete editor also appears in matching settings-search results and follows
the same searchable-container contract as future full-width settings panels.
War → Territory Map also contains the disabled-by-default **HQ Distance and Queue
Estimate** option. With Wynntils 4.2.7 or newer installed, holding either Ctrl key
over a guild-map territory shows its shortest link distance from the player's HQ
through any guild's territories and, except at HQ, the provisional
`60 + 60 × connections` queue estimate prefixed with a clock glyph. Distance
color interpolates from green at HQ through yellow to red according to the guild's
territory count. Red starts at `round(1.25 × sqrt(territories owned))` connections
(at least one), yellow at 60% of that threshold, and greater distances stay red.
Owned destinations are hypothetical. Active attack timers suppress both added
labels. Labels stay at native size at all zoom levels, even when they overlap
or extend beyond territory bounds. Missing routes or an uncertain HQ remain unknown; stale ownership
does not block known links through other guilds. RALLE reuses Wynntils' existing models and does
not add polling or backend traffic. The historical duration formula remains a
release blocker until it is checked against current in-game attack previews.

War → Attack Timers contains the separate disabled-by-default **Show Who
Queued** option for Wynntils 4.2.7 or newer. It prefixes each row in Wynntils'
existing Guild Attack Timer with the first observed defense-announcement sender:
the local IGN is blue, other IGNs are gray, and missed or unresolved senders show
as gray `Unknown`. Enable Wynntils' Guild Attack Timer overlay to see the rows;
senders must also have defense announcements enabled for their names to be
observable. RALLE does not change either Wynntils setting. Attribution is kept
only in memory and survives world/server switches, character selection, and
reconnects for the same account and guild while the matching countdown remains
unexpired. Captures, clearly different countdowns, account/guild changes, and
disabling the option discard old names. Messages received before the option was
enabled cannot be reconstructed, and a manually typed guild message identical to the automatic
announcement cannot be distinguished from it.

The first connection after a new installation posts one local RALLE onboarding
message with clickable settings, LFG, and HUD commands. Its numeric sent marker
is stored in `config/ralle-onboarding.properties`; existing RALLE configs or
saved vanilla RALLE keybinds are migrated as already seen.

## Development

Requirements: Java 21.

```text
./gradlew build
./gradlew runClient
```

Import the repository root as a Gradle project in IntelliJ IDEA and select a
Java 21 Gradle JVM.

See `docs/ARCHITECTURE.md` for the intended module boundaries.
