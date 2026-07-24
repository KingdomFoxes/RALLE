package org.kingdomfoxes.ralle.ui.owo;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Screen-local delay before an explicit Raid LFG join mutation is submitted. */
final class JoinCountdownState {
    static final Duration DURATION = Duration.ofSeconds(3);

    private final LongSupplier nanoTime;
    private UUID lobbyId;
    private long startedAtNanos;

    JoinCountdownState(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    boolean start(UUID lobbyId) {
        if (active()) return false;
        this.lobbyId = lobbyId;
        this.startedAtNanos = nanoTime.getAsLong();
        return true;
    }

    boolean active() {
        return lobbyId != null;
    }

    boolean activeFor(UUID lobbyId) {
        return lobbyId.equals(this.lobbyId);
    }

    UUID lobbyId() {
        return lobbyId;
    }

    int secondsRemaining() {
        long remaining = remainingNanos();
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 1_000_000_000d);
    }

    double remainingFraction() {
        return Math.clamp(remainingNanos() / (double) DURATION.toNanos(), 0d, 1d);
    }

    boolean elapsed() {
        return active() && remainingNanos() <= 0;
    }

    void cancel() {
        lobbyId = null;
        startedAtNanos = 0;
    }

    private long remainingNanos() {
        if (!active()) return 0;
        return DURATION.toNanos() - (nanoTime.getAsLong() - startedAtNanos);
    }
}
