# Bounded HTTP responses

`BoundedHttpBody.send` is the shared HTTP response collector for Raid LFG, guild
ranks, public Wynncraft membership checks, cosmetic lookups/style selection and
Modrinth updates. Each caller retains its existing endpoint, authentication,
status handling, protocol decoder and byte limit. No credentials are persisted.

The body subscriber counts bytes before forwarding each received buffer to the
bounded accumulator. An oversized body cancels the subscription and fails with
`BodyLimitException`. UTF-8 decoding happens only after all accepted chunks have
arrived, so a multibyte character split between network buffers stays intact.
There are no blocking InputStream reads on HTTP completion threads.

The request's timeout (12 seconds for these integrations) covers collection
through the final body byte. A separate result future applies the deadline;
failure/timeout/cancellation cancels the original transport future with
`cancel(true)`. Keeping the futures separate is essential: applying `orTimeout`
to the transport future itself would complete it before cancellation could
abort its stalled body. No extra timeout executor is owned by the mod.

Raid LFG still retries a transport failure at most once using the same bearer
and idempotency key. Exhausted body timeouts remain transport failures and feed
the existing unknown-mutation synchronization path. Oversized documents retain
their protocol-error classification and are not retried as transport failures.

`BoundedHttpBodyTest` uses a real local HTTP server which sends headers and an
initial byte, then stalls. The test checks both deadline failure and actual
HTTP-client termination while the server is still stalled. Separate subscriber
checks cover split UTF-8 and cancellation before accepting an oversized batch.
Existing gateway/service tests cover status errors, size limits, retry keys and
uncertain mutation outcomes.
