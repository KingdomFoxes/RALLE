package org.kingdomfoxes.ralle.classes;

/** Adaptive exponential-in reveal; the server, not this effect, owns the release deadline. */
public final class ClassIceTimeline {
    private long started, closed;
    private boolean finishing;
    private double atClose, exponent;
    public void start(long now) { started = now; finishing = false; atClose = 0; }
    public void bookClosed(long now) {
        if (finishing) return;
        atClose = progress(now);
        // Match the incoming velocity instead of resetting to a slower fixed ease.
        double velocity = .16 / 2400 * Math.exp(-Math.max(0, now - started) / 2400d);
        double ratio = velocity * (ClassSwitchController.ANIMATION_MILLIS - 100) / (1 - atClose);
        double low = 0, high = 32;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2;
            if (mid / Math.expm1(mid) > ratio) low = mid;
            else high = mid;
        }
        exponent = (low + high) / 2;
        closed = now;
        finishing = true;
    }
    public double progress(long now) {
        if (!finishing) return .16 * (1 - Math.exp(-Math.max(0, now - started) / 2400d));
        // Reserve the final 100 ms for complete coverage, without delaying controller release.
        double t = Math.clamp((now - closed) / (double) (ClassSwitchController.ANIMATION_MILLIS - 100), 0, 1);
        return atClose + (1 - atClose) * Math.expm1(exponent * t) / Math.expm1(exponent);
    }
}
