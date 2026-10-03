package org.kingdomfoxes.ralle.update;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Client-thread login notice. Public metadata is shared across rapid reconnects for one minute. */
public final class UpdateNotice {
    private static final long CACHE_MILLIS = 60_000;
    private final Supplier<CompletableFuture<Optional<ModrinthRelease>>> lookup;
    private final LongSupplier clock;
    private CompletableFuture<Optional<ModrinthRelease>> pending;
    private Optional<ModrinthRelease> cached = Optional.empty();
    private long nextLookup;
    private boolean hasChecked;
    private boolean awaitingNotice;

    public UpdateNotice(Supplier<CompletableFuture<Optional<ModrinthRelease>>> lookup, LongSupplier clock) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void joined() { awaitingNotice = true; }
    public void disconnected() { awaitingNotice = false; }

    /** Returns a result once per multiplayer login, never while disconnected or on a worker thread. */
    public Optional<ModrinthRelease> tick() {
        if (!awaitingNotice) return Optional.empty();
        if (pending != null) {
            if (!pending.isDone()) return Optional.empty();
            try { cached = Objects.requireNonNull(pending.join(), "lookup result"); }
            catch (RuntimeException failure) { cached = Optional.empty(); }
            pending = null;
            if (clock.getAsLong() < nextLookup) {
                awaitingNotice = false;
                return cached;
            }
        }
        if (hasChecked && clock.getAsLong() < nextLookup) {
            awaitingNotice = false;
            return cached;
        }
        hasChecked = true;
        nextLookup = clock.getAsLong() + CACHE_MILLIS;
        cached = Optional.empty();
        try { pending = Objects.requireNonNull(lookup.get(), "lookup future"); }
        catch (RuntimeException failure) { awaitingNotice = false; }
        return Optional.empty();
    }
}
