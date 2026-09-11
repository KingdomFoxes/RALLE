package org.kingdomfoxes.ralle.ui.owo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-independent annular selection, lifecycle and independently reversible segment motion. */
public final class SelectorWheelModel {
    public static final long ANIMATION_MILLIS = 120;
    public static final double EXTENSION = 5;
    public enum State { CLOSED, SELECTING, SUBMITTED, AWAITING_RELEASE }
    private State state = State.CLOSED;
    private int hovered = -1;
    private final Map<Integer, Motion> motions = new HashMap<>();

    public void open(long now) { state = State.SELECTING; hovered = -1; motions.clear(); }
    public State state() { return state; }
    public int hovered() { return hovered; }
    public boolean ownsInput() { return state == State.SELECTING || state == State.SUBMITTED; }
    public void hover(int option, long now) {
        if (state != State.SELECTING || option == hovered) return;
        if (hovered >= 0) motions.put(hovered, new Motion(offset(hovered, now), 0, now));
        if (option >= 0) motions.put(option, new Motion(offset(option, now), EXTENSION, now));
        hovered = option;
    }
    public double offset(int option, long now) {
        var motion = motions.get(option);
        if (motion == null) return 0;
        double t = Math.clamp((now - motion.started()) / (double) ANIMATION_MILLIS, 0, 1);
        return motion.from() + (motion.to() - motion.from()) * (1 - Math.pow(1 - t, 3));
    }
    public boolean submit() {
        if (state != State.SELECTING || hovered < 0) return false;
        state = State.SUBMITTED;
        return true;
    }
    public void cancel() { if (ownsInput()) state = State.AWAITING_RELEASE; }
    public void released() { state = State.CLOSED; }

    public static List<Sector> ring(int count, double inner, double outer, boolean firstAtTop) {
        if (count < 0 || inner < 0 || outer <= inner) throw new IllegalArgumentException("Invalid ring");
        var sectors = new ArrayList<Sector>();
        for (int i = 0; i < count; i++) {
            double angle = (firstAtTop ? -Math.PI / 2 : Math.PI) + i * Math.PI * 2 / count;
            sectors.add(new Sector(inner, outer, angle, Math.PI / count));
        }
        return List.copyOf(sectors);
    }
    public static int hit(List<Sector> sectors, double x, double y) {
        for (int i = 0; i < sectors.size(); i++) {
            // Stable resting geometry plus its full outward envelope, independent of animation.
            var s = sectors.get(i);
            for (int offset = 0; offset <= EXTENSION; offset++) {
                if (s.contains(x - Math.cos(s.angle()) * offset, y - Math.sin(s.angle()) * offset, 0)) return i;
            }
        }
        return -1;
    }
    public record Sector(double inner, double outer, double angle, double halfAngle) {
        public boolean contains(double x, double y, double inset) {
            double radius = Math.hypot(x, y);
            if (radius < inner + inset || radius >= outer - inset) return false;
            if (halfAngle >= Math.PI) return true;
            double delta = Math.abs(Math.atan2(Math.sin(Math.atan2(y, x) - angle),
                    Math.cos(Math.atan2(y, x) - angle)));
            return delta < halfAngle && radius * Math.sin(halfAngle - delta) >= 1 + inset;
        }
        public double centerX() { return Math.cos(angle) * (inner + outer) / 2; }
        public double centerY() { return Math.sin(angle) * (inner + outer) / 2; }
    }
    private record Motion(double from, double to, long started) {}
}
