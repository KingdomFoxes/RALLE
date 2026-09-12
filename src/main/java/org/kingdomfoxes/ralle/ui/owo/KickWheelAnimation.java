package org.kingdomfoxes.ralle.ui.owo;

/** The supplied Realistic Explosion reference has 17 frames at 80 ms each. */
final class KickWheelAnimation {
    static final int FRAMES = 17;
    static final long FRAME_MILLIS = 80;
    static int frame(long started, long now) {
        long frame = Math.max(0, now - started) / FRAME_MILLIS;
        return frame >= FRAMES ? -1 : (int) frame;
    }
    static boolean finished(long started, long now) { return frame(started, now) < 0; }
}
