# Live connection renewal and party invitations

`RaidLfgService` keeps short-lived Fox credentials in memory. A
`session_expiring` frame starts one parallel authentication and live connection
attempt; repeated warnings share that attempt and respect retry backoff. The
existing connection continues delivering updates until the replacement has both
its connection handle and a complete authoritative snapshot. Updates arriving
on the replacement before its connection future completes are buffered, bounded
to 128 frames, and applied after that snapshot. If the old credential expires
first, LFG becomes read-only until fresh synchronization succeeds.

Committing a renewal promotes the replacement listener to the active connection
generation. Every later frame, close, and error goes through the ordinary live
handlers, including the next expiry warning. Clearing the temporary renewal
attempt must not disable that listener. Superseded sockets retain their old
generation, so late callbacks cannot alter the current state or restart it.
Closing a superseded socket sends one graceful close frame and aborts its
transport after one second, even if the peer never completes the handshake.
Repeated closes share this cleanup; they never send duplicate close frames.
Successful renewal does not extend a credential's backend lifetime or change
Fox's authorization rules.

`HostPartyInviteController` offers invitations when a host lobby fills live.
Without a persistent card, only a genuine live fill produces the chat prompt.
A complete snapshot confirming a full hosted lobby also restores its persistent
card's manual `Party filled` action, silently. Connection recovery must not
replay chat prompts or automatically send party commands. Existing card offers
survive temporary synchronization loss but stay unavailable until the service
is online again; loss of membership/host authority or an unfilled roster clears
them. Pending commands are canceled whenever the service is not online.

An explicit invitation rechecks the synchronized host and current canonical
IGNs, queues at most three non-host targets, and sends one `/pa` invite every
600 ms. No invitation is inferred or sent from stale, offline state. The
renewal, post-renewal streaming, early-callback ordering, disconnect recovery,
and snapshot-to-invite flows are covered by the neighboring service and host
invitation tests.
