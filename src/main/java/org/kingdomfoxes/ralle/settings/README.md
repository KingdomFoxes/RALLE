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

LFG controls default to F1 (browser), F2 (join), F3 (close), F4 (leave/disband),
F5 (party filled), F6 (ping), F7 (lock), F8 (create), and F9 (kick). Automatic
raid requeue remains unbound; HQ inspection retains Left Ctrl. These are
configurable and may conflict with vanilla or other mod bindings.

`RalleOnboardingNotice` saves a pending local state before default-config
creation. Restarting before the initial connection therefore does not mistake
the generated config for an older installation and suppress the welcome.
The original welcome includes clickable `/ralle settings`, `/ralle lfg`, and
`/ralle hud` commands, is delivered once, and does not repeat on upgrades.
