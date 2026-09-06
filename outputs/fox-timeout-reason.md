# Fox timeout update

Implemented directly in Desktop/Fox-main and pushed to [PR #29](https://github.com/KingdomFoxes/Fox/pull/29)
as commit `fde33826373bbecf0f106324bd5063f649dd4fe9`.

This supersedes the earlier isolated bridge. The accompanying patch now represents
that PR commit against its parent `6e0c4cd`; no manual application is needed.

Parties that never reach four members time out after 30 minutes from creation.
Once filled, they remain exempt after reopening or inactivity. Existing listings
are grandfathered on database upgrade because historical fill state is unknown.
Discord and the updated mod recognize authoritative timeout events for partial
rosters too. The mod requests the optional reason field through a capability header,
so older clients retain the legacy event shape.

Verified: 493 backend tests, 69 Discord LFG tests, and the matching mod full build.
PR updated; no merge or deployment performed.
