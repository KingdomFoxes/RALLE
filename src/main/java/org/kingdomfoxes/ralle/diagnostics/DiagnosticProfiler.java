package org.kingdomfoxes.ralle.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Opt-in, fixed-size inclusive measurements. No clocks, allocations or locks on the disabled path. */
public final class DiagnosticProfiler {
    public enum Section {
        CHAT_LAYOUT_TICK, CHAT_BEHAVIOR_TICK, CHAT_INPUT_TICK, GUILD_RANK_TICK,
        CHAT_SCREENSHOT_TICK, LFG_SERVICE_TICK, LFG_KEYBINDS_TICK, REQUEUE_TICK,
        PARTY_INVITES_TICK, NOTIFICATIONS_TICK, QUEUE_ATTRIBUTION_TICK,
        CHAT_PROJECTION, CHAT_SHADOW_PREPARE, CHAT_CAPTURE_SUBMIT, CHAT_CAPTURE_READBACK,
        CHAT_PNG_CLIPBOARD, LFG_GRID_REBUILD, LFG_OVERLAY_RENDER, SELECTOR_RENDER,
        HQ_INSPECTION, CONSUMABLE_MATCH
    }

    private static volatile Session active;
    private static final Scope DISABLED = new Scope(null, null, 0, -1, 0);

    private DiagnosticProfiler() {}

    public static Scope measure(Section section) {
        var session = active;
        if (session == null) return DISABLED;
        return session.measure(section);
    }

    public static synchronized Session start(int seconds, LongSupplier allocatedBytes) {
        if (active != null) throw new IllegalStateException("A diagnostic recording is already running");
        active = new Session(seconds, System::nanoTime, allocatedBytes);
        return active;
    }

    public static synchronized void stop(Session session) {
        if (active == session) active = null;
        session.finish();
    }

    public static final class Scope implements AutoCloseable {
        private final Session session;
        private final Section section;
        private final long started, allocated, thread;
        private boolean closed;

        private Scope(Session session, Section section, long started, long allocated, long thread) {
            this.session = session;
            this.section = section;
            this.started = started;
            this.allocated = allocated;
            this.thread = thread;
        }

        @Override public void close() {
            if (session == null || closed) return;
            closed = true;
            if (thread != Thread.currentThread().threadId()) return;
            long elapsed = Math.max(0, session.clock.getAsLong() - started);
            long endBytes = session.allocatedBytes.getAsLong();
            session.record(section, elapsed, allocated >= 0 && endBytes >= allocated ? endBytes - allocated : -1);
        }
    }

    public static final class Session {
        private final LongSupplier clock, allocatedBytes;
        private final long started, durationNanos;
        private final Stats[] stats = new Stats[Section.values().length];
        private boolean finished;

        Session(int seconds, LongSupplier clock, LongSupplier allocatedBytes) {
            if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Duration must be 1–300 seconds");
            this.clock = clock;
            this.allocatedBytes = allocatedBytes;
            this.started = clock.getAsLong();
            this.durationNanos = seconds * 1_000_000_000L;
            for (int i = 0; i < stats.length; i++) stats[i] = new Stats();
        }

        public boolean expired() { return clock.getAsLong() - started >= durationNanos; }

        Scope measure(Section section) {
            long now = clock.getAsLong();
            if (now - started >= durationNanos) return DISABLED;
            return new Scope(this, section, now, allocatedBytes.getAsLong(), Thread.currentThread().threadId());
        }

        synchronized void record(Section section, long nanos, long bytes) {
            if (finished) return;
            var stat = stats[section.ordinal()];
            stat.calls++;
            stat.totalNanos += nanos;
            stat.maxNanos = Math.max(stat.maxNanos, nanos);
            if (bytes >= 0) { stat.allocationSamples++; stat.allocatedBytes += bytes; }
        }

        synchronized void finish() { finished = true; }

        public synchronized List<Measurement> snapshot() {
            var result = new ArrayList<Measurement>();
            for (var section : Section.values()) {
                var s = stats[section.ordinal()];
                result.add(new Measurement(section.name(), s.calls, s.totalNanos, s.maxNanos,
                        s.allocationSamples, s.allocatedBytes));
            }
            return List.copyOf(result);
        }
    }

    private static final class Stats {
        long calls, totalNanos, maxNanos, allocationSamples, allocatedBytes;
    }

    public record Measurement(String section, long calls, long totalNanos, long maxNanos,
                              long allocationSamples, long allocatedBytes) {}
}
