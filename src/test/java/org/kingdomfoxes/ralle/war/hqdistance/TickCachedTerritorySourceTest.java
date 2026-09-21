package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class TickCachedTerritorySourceTest {
    @Test void samplesOnDemandOncePerTickButNeverCachesAttackTimers() {
        var tick = new AtomicLong();
        class Source implements TerritorySnapshotSource {
            int reads;
            boolean timer;
            TerritorySnapshot current = new TerritorySnapshot("Fox", Map.of(), false, false);
            public TerritorySnapshot snapshot() { reads++; return current; }
            public boolean hasActiveAttackTimer(String name) { return timer; }
        }
        var source = new Source();
        var cached = new TickCachedTerritorySource(source, tick::get);
        assertEquals(0, source.reads);
        var first = cached.snapshot();
        for (int frame = 0; frame < 200; frame++) assertSame(first, cached.snapshot());
        assertEquals(1, source.reads);
        source.timer = true;
        assertTrue(cached.hasActiveAttackTimer("any"));
        source.current = new TerritorySnapshot("Other", Map.of(), false, false);
        tick.incrementAndGet();
        assertSame(source.current, cached.snapshot());
        assertEquals(2, source.reads);
        tick.addAndGet(100);
        assertEquals(2, source.reads);
    }
}
