package org.kingdomfoxes.ralle.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.kingdomfoxes.ralle.diagnostics.DiagnosticProfiler.Section.*;

class DiagnosticProfilerTest {
    @Test void disabledPathReusesOneScope() {
        assertSame(DiagnosticProfiler.measure(CHAT_PROJECTION), DiagnosticProfiler.measure(HQ_INSPECTION));
    }

    @Test void recordsElapsedAndAllocationAndClosesOnlyOnce() {
        var clock = new AtomicLong(10);
        var bytes = new AtomicLong(50);
        var session = new DiagnosticProfiler.Session(1, clock::get, bytes::get);
        var scope = session.measure(CHAT_PROJECTION);
        clock.addAndGet(20);
        bytes.addAndGet(80);
        scope.close();
        scope.close();
        var result = session.snapshot().get(CHAT_PROJECTION.ordinal());
        assertEquals(1, result.calls());
        assertEquals(20, result.totalNanos());
        assertEquals(20, result.maxNanos());
        assertEquals(80, result.allocatedBytes());
        assertEquals(1, result.allocationSamples());
    }

    @Test void timeoutPreventsNewSamplesEvenWithoutClientTicks() {
        var clock = new AtomicLong();
        var session = new DiagnosticProfiler.Session(1, clock::get, () -> { fail("allocation counter after expiry"); return 0L; });
        clock.set(1_000_000_000L);
        assertTrue(session.expired());
        session.measure(HQ_INSPECTION).close();
        assertEquals(0, session.snapshot().get(HQ_INSPECTION.ordinal()).calls());
    }

    @Test void unsupportedAllocationRemainsExplicit() {
        var session = new DiagnosticProfiler.Session(1, () -> 0, () -> -1);
        session.measure(CHAT_PNG_CLIPBOARD).close();
        var result = session.snapshot().get(CHAT_PNG_CLIPBOARD.ordinal());
        assertEquals(1, result.calls());
        assertEquals(0, result.allocationSamples());
    }

    @Test void lateCompletionCannotChangeFinishedReport() {
        var session = new DiagnosticProfiler.Session(1, () -> 0, () -> 0);
        var scope = session.measure(CHAT_PNG_CLIPBOARD);
        session.finish();
        scope.close();
        assertEquals(0, session.snapshot().get(CHAT_PNG_CLIPBOARD.ordinal()).calls());
    }

    @Test void nestedSectionsAreInclusive() {
        var clock = new AtomicLong();
        var session = new DiagnosticProfiler.Session(1, clock::get, () -> 0);
        var outer = session.measure(LFG_KEYBINDS_TICK);
        clock.set(10);
        var inner = session.measure(SELECTOR_RENDER);
        clock.set(30);
        inner.close();
        clock.set(50);
        outer.close();
        assertEquals(50, session.snapshot().get(LFG_KEYBINDS_TICK.ordinal()).totalNanos());
        assertEquals(20, session.snapshot().get(SELECTOR_RENDER.ordinal()).totalNanos());
    }

    @Test void activeSessionIsExclusiveAndCanBeRestarted() {
        var first = DiagnosticProfiler.start(1, () -> -1);
        try { assertThrows(IllegalStateException.class, () -> DiagnosticProfiler.start(1, () -> -1)); }
        finally { DiagnosticProfiler.stop(first); }
        var next = DiagnosticProfiler.start(1, () -> -1);
        try { DiagnosticProfiler.measure(CHAT_LAYOUT_TICK).close(); }
        finally { DiagnosticProfiler.stop(next); }
        assertEquals(1, next.snapshot().get(CHAT_LAYOUT_TICK.ordinal()).calls());
        assertEquals(0, first.snapshot().get(CHAT_LAYOUT_TICK.ordinal()).calls());
    }

    @Test void rejectsUnboundedDurations() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticProfiler.Session(0, () -> 0, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticProfiler.Session(301, () -> 0, () -> 0));
    }

    @Test void workerSamplesAreAggregatedSafely() throws Exception {
        var session = new DiagnosticProfiler.Session(300, System::nanoTime, () -> -1);
        var workers = new java.util.ArrayList<Thread>();
        for (int i = 0; i < 4; i++) {
            var worker = new Thread(() -> {
                for (int j = 0; j < 1000; j++) session.measure(CHAT_PNG_CLIPBOARD).close();
            });
            workers.add(worker);
            worker.start();
        }
        for (var worker : workers) worker.join();
        assertEquals(4000, session.snapshot().get(CHAT_PNG_CLIPBOARD.ordinal()).calls());
    }
}
