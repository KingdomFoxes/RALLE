package org.kingdomfoxes.ralle.client;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Client-only settings access. Fox still independently authorizes every backend action.
 * One fresh UUID lookup per Wynncraft login, with at most two delayed retries after failure.
 * No persisted membership or access from a previous connection is reused.
 */
public final class FoxGuildAccess {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_MILLIS = 30_000;
    private final Function<UUID, CompletableFuture<Boolean>> lookup;
    private final LongSupplier clock;
    private UUID player;
    private CompletableFuture<Boolean> pending;
    private int attempts;
    private long nextAttempt;
    private boolean resolved;
    private volatile boolean allowed;

    public FoxGuildAccess(Function<UUID, CompletableFuture<Boolean>> lookup, LongSupplier clock) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void joined(String host, UUID player) {
        clear();
        if (WynncraftHost.matches(host)) this.player = Objects.requireNonNull(player, "player");
    }

    public void clear() {
        player = null;
        pending = null;
        attempts = 0;
        nextAttempt = 0;
        resolved = false;
        allowed = false;
    }

    public boolean allowed() { return allowed; }

    /** Runs before feature ticks; asynchronous replies can only change access here. */
    public void tick() {
        if (player == null || resolved) return;
        if (pending != null) {
            if (!pending.isDone()) return;
            try {
                allowed = Boolean.TRUE.equals(pending.join());
                resolved = true;
            } catch (RuntimeException failure) {
                allowed = false;
                nextAttempt = clock.getAsLong() + RETRY_MILLIS;
            }
            pending = null;
        }
        if (!resolved && attempts < MAX_ATTEMPTS && clock.getAsLong() >= nextAttempt) {
            attempts++;
            try {
                pending = Objects.requireNonNull(lookup.apply(player), "lookup future");
            } catch (RuntimeException failure) {
                nextAttempt = clock.getAsLong() + RETRY_MILLIS;
            }
        }
    }
}
