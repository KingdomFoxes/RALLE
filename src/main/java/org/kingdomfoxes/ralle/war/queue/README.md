# Wynntils Guild Attack Timers

Show Who Queued decorates Wynntils' existing timer rows and editor preview.
On the first usable client tick with the setting enabled, RALLE also widens an
untouched Wynntils `250 x 110` timer box to `370 x 110` logical pixels. The extra
120 pixels reserve room for a full 16-character IGN and the arrow prefix.
This check uses the loaded setting, so existing enabled 1.0.0 installations get
the same adjustment without toggling the option again.

RALLE skips any size Wynntils reports as edited or different from the original
dimensions. It copies the size and uses Wynntils' config setter and persistence
API so the wrapping width refreshes immediately and the adjusted box survives
restarts. No extra RALLE saved setting is needed: Wynntils' edited-size state
protects subsequent manual changes, including a manually saved original width.
The check is attempted only once per client session after the overlay config is
available. Disabling the option does not restore a previous size. Position,
height, alignment, font, shadow, ordering, and ordinary wrapping remain owned by
Wynntils; players can still shrink the box and allow rows to wrap.
