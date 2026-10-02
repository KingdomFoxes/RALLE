package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionCosmeticCacheTest {
    @Test
    void ttlRequestsRefreshWhileRevocationAndSessionClearReplacePresentation() {
        var clock = new MutableClock();
        var cache = new SessionCosmeticCache(clock);
        UUID uuid = UUID.randomUUID();
        var supporter = new CosmeticIdentity(uuid, Set.of("supporter"), "supporter-gold", 1);
        cache.put(supporter, Duration.ofSeconds(60));
        assertEquals(supporter, cache.get(uuid));
        assertFalse(cache.needsRefresh(uuid));
        clock.advance(30_000);
        assertEquals(supporter, cache.get(uuid));
        assertTrue(cache.needsRefresh(uuid));
        cache.put(supporter, Duration.ofSeconds(30));
        cache.put(CosmeticIdentity.neutral(uuid, 2), Duration.ofSeconds(30));
        assertNull(cache.get(uuid).selectedStyle());
        cache.clear();
        assertNull(cache.get(uuid));
        assertTrue(cache.needsRefresh(uuid));
        assertThrows(IllegalArgumentException.class,
                () -> new CosmeticIdentity(uuid, Set.of("supporter"), "admin-red", 2));
        assertThrows(IllegalArgumentException.class,
                () -> new CosmeticIdentity(uuid, Set.of("engineer"), null, 2));
    }

    @Test
    void boundsEntriesAndHonorsShorterServerTtl() {
        var clock = new MutableClock();
        var cache = new SessionCosmeticCache(clock);
        UUID first = new UUID(0, 1);
        for (int i = 1; i <= SessionCosmeticCache.MAX_ENTRIES + 1; i++)
            cache.put(CosmeticIdentity.neutral(new UUID(0, i), 0), Duration.ofSeconds(5));
        assertEquals(SessionCosmeticCache.MAX_ENTRIES, cache.size());
        assertNull(cache.get(first));
        clock.advance(5_000);
        assertNotNull(cache.get(new UUID(0, 2)));
        assertTrue(cache.needsRefresh(new UUID(0, 2)));
    }

    @Test
    void zeroTtlDoesNotBlankTheAcceptedPresentation() {
        var cache = new SessionCosmeticCache(new MutableClock());
        UUID uuid = UUID.randomUUID();
        var identity = new CosmeticIdentity(uuid, Set.of("supporter"), "supporter-gold", 1);
        cache.put(identity, Duration.ZERO);
        assertEquals(identity, cache.get(uuid));
        assertTrue(cache.needsRefresh(uuid));
    }

    private static final class MutableClock extends Clock {
        private long millis;
        void advance(long amount) { millis += amount; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
