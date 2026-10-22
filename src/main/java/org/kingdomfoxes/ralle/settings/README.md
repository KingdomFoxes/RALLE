# Installation defaults

On first initialization, `SettingsRegistry.seal()` writes `config/ralle.properties`
if it does not exist. Existing files and saved preferences are loaded without
being rewritten during initialization; missing or invalid entries use their
registered defaults. Settings continue to save locally after changes.

The restored installation preset enables chat screenshots with Snap to Text,
chat selection sounds, persistent chat with a 300-message session history,
Raid LFG, new/reopened party notifications, party status notifications,
auto pop-out, and notification sounds. Other optional toggles remain off.
Supporter nameplates remain always on. Raid LFG's saved default is true, but its
effective value stays false until the current login confirms Fox membership;
backend authentication and eligibility checks still apply.

## Locked preferences

Class Switcher's toggle requires the account's last observed Auto Open flag to
be true. The flag persists in `ralle-class-shortcuts.json` across connections and
restarts; missing values default to off. Its passive prerequisite check runs
while the feature is off and saves only that on/off flag. It does not discover
characters or capture diagnostics. A newly observed disabled value locks the
toggle again. Direct mutations and saved Class Switcher preferences cannot bypass
the gate. The original Class Switcher description is retained; unavailable-row
guidance explains Auto Open. See `docs/class-selector.md`.

Access is enforced by `Setting.value()` and `Setting.set()`, including callers
outside the settings screen. Fox-exclusive booleans resolve to off until the
current login confirms membership. Supporter local preferences reuse fresh,
current-account grants from the existing bounded Fox cosmetic lookup: Show Own
Nametag resolves to off and Material Resolution to `1` while locked. No additional
request is made when changing a setting. Personal style selection remains a
Fox-authorized mutation and has no editable local style preference.

Direct mutations while locked leave the prior saved preference intact. Values
loaded from a manually edited config cannot bypass runtime access; unrelated
saves preserve them pending entitlement confirmation. Disconnect, account/server
change, missing/expired grants, and revocation immediately restore safe runtime
values. Confirmed access restores the saved preference, including legacy material
resolution choices. These client checks do not replace backend authorization.

LFG controls default to F1 (browser), F2 (join), F3 (close), F4 (leave/disband),
F5 (party filled), F6 (ping), F7 (lock), F8 (create), and F9 (kick). Automatic
raid requeue remains unbound; HQ inspection retains Left Ctrl. These are
configurable and may conflict with vanilla or other mod bindings.

`RalleOnboardingNotice` saves a pending local state before default-config
creation. Restarting before the initial connection therefore does not mistake
the generated config for an older installation and suppress the welcome.
The original welcome includes clickable `/ralle settings`, `/ralle lfg`, and
`/ralle hud` commands, is delivered once, and does not repeat on upgrades.
