package org.kingdomfoxes.ralle.ui.owo;

/** Deterministic individual-pixel erosion followed by a brief empty-sector hold. */
final class CreateWheelAnimation {
    static final long HOLD_MILLIS = 100;
    static final double SOUND_DURATION_FRACTION = .8;
    private final long started;
    private final long duration;

    CreateWheelAnimation(long started, long duration) {
        this.started = started;
        this.duration = Math.max(1, Math.round(duration * SOUND_DURATION_FRACTION));
    }

    double progress(long now) { return Math.clamp((now - started) / (double) duration, 0, 1); }
    boolean finished(long now) { return now - started >= duration + HOLD_MILLIS; }
    double extension(long now) {
        double t = Math.clamp((now - started) / 180d, 0, 1);
        return 5 * (1 - Math.pow(1 - t, 3));
    }

    private static int hash(int x, int y) {
        int hash = x * 0x1f123bb5 ^ y * 0x5f356495;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return hash;
    }

    static double threshold(int x, int y) {
        return .12 + .52 * ((hash(x, y) & 0xffff) / 65535d);
    }

    static double opacity(int x, int y, double progress) {
        return 1 - Math.clamp((progress - threshold(x, y)) / .36, 0, 1);
    }

    static boolean visible(int x, int y, double progress) {
        return progress < threshold(x, y) + .18;
    }
}
