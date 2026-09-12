package org.kingdomfoxes.ralle.ui.owo;

/** Premade individual-pixel erosion followed by a brief empty-sector hold. */
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

    static int frame(double progress) {
        return (int) Math.floor(Math.clamp(progress, 0, 1) * 63);
    }
}
