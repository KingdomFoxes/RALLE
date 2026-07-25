package org.kingdomfoxes.ralle.ui.owo;

import java.util.UUID;
import java.util.function.LongSupplier;

/** Local-only state for the host's cancellable 1.5-second hold-to-kick interaction. */
final class KickTargetingState {
    static final long HOLD_NANOS = 1_500_000_000L;
    static final long CANCEL_FADE_NANOS = 250_000_000L;

    private final LongSupplier nanoTime;
    private boolean active;
    private UUID hovered;
    private UUID holding;
    private UUID submitted;
    private long holdStartedAt;
    private long cancelFadeStartedAt;
    private double cancelFadeStartedProgress;

    KickTargetingState(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    boolean active() {
        return active;
    }

    UUID hovered() {
        return hovered;
    }

    UUID visualTarget() {
        return holding != null ? holding : hovered;
    }

    boolean holding() {
        return holding != null;
    }

    boolean submitted() {
        return submitted != null;
    }

    boolean toggle() {
        if (active) {
            reset();
            return false;
        }
        active = true;
        return true;
    }

    boolean hover(UUID target) {
        if (!active || submitted != null || target == null || target.equals(hovered)) return false;
        hovered = target;
        return true;
    }

    boolean leave(UUID target) {
        if (!active || target == null || !target.equals(hovered)) return false;
        hovered = null;
        return cancelHold();
    }

    boolean press(UUID target) {
        if (!active || submitted != null || target == null || !target.equals(hovered)) return false;
        holding = target;
        holdStartedAt = nanoTime.getAsLong();
        cancelFadeStartedAt = 0L;
        cancelFadeStartedProgress = 0d;
        return true;
    }

    boolean release(UUID target) {
        if (holding == null || target == null || !target.equals(holding)) return false;
        return cancelHold();
    }

    UUID completedTarget() {
        if (holding == null || submitted != null || nanoTime.getAsLong() - holdStartedAt < HOLD_NANOS) {
            return null;
        }
        submitted = holding;
        holding = null;
        return submitted;
    }

    double progress() {
        if (submitted != null) return 1d;
        long now = nanoTime.getAsLong();
        if (holding != null) return holdProgress(now);
        if (cancelFadeStartedProgress == 0d) return 0d;
        double fade = (now - cancelFadeStartedAt) / (double) CANCEL_FADE_NANOS;
        if (fade >= 1d) {
            cancelFadeStartedAt = 0L;
            cancelFadeStartedProgress = 0d;
            return 0d;
        }
        return cancelFadeStartedProgress * (1d - Math.clamp(fade, 0d, 1d));
    }

    void reset() {
        active = false;
        hovered = null;
        holding = null;
        submitted = null;
        holdStartedAt = 0L;
        cancelFadeStartedAt = 0L;
        cancelFadeStartedProgress = 0d;
    }

    private boolean cancelHold() {
        if (holding == null) return false;
        long now = nanoTime.getAsLong();
        cancelFadeStartedProgress = holdProgress(now);
        cancelFadeStartedAt = now;
        holding = null;
        holdStartedAt = 0L;
        return true;
    }

    private double holdProgress(long now) {
        return Math.clamp((now - holdStartedAt) / (double) HOLD_NANOS, 0d, 1d);
    }
}
