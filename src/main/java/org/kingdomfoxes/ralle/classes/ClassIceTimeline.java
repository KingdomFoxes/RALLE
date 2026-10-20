package org.kingdomfoxes.ralle.classes;

/** Adaptive exponential-in reveal; the server, not this effect, owns the release deadline. */
public final class ClassIceTimeline {
    private long started, closed;
    private boolean finishing;
    private double atClose;
    public void start(long now) { started = now; finishing = false; atClose = 0; }
    public void bookClosed(long now) {
        if (finishing) return;
        atClose = progress(now);
        closed = now;
        finishing = true;
    }
    public double progress(long now) {
        if (!finishing) return .16 * (1 - Math.exp(-Math.max(0, now - started) / 2400d));
        // Reserve the final 100 ms for complete coverage, without delaying controller release.
        double t = Math.clamp((now - closed) / (double) (ClassSwitchController.ANIMATION_MILLIS - 100), 0, 1);
        return atClose + (1 - atClose) * Math.expm1(5 * t) / Math.expm1(5);
    }
}
