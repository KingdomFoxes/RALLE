package org.kingdomfoxes.ralle.ui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAsyncCacheTest {
    @Test void boundsThousandsOfIdentitiesAndReleasesCompletedFutures() {
        var cache = new BoundedAsyncCache<Integer, String>(256, 8, 1000, () -> 0L);
        for (int id = 0; id < 10_000; id++) {
            cache.get(id, "fallback", key -> CompletableFuture.completedFuture("skin" + key));
            assertTrue(cache.size() <= 256);
            assertEquals(0, cache.inFlight());
        }
        assertEquals("skin9999", cache.get(9999, "fallback", key -> fail("Should be cached")));
    }

    @Test void pendingWorkStaysBoundedAcrossClearAndCannotRepopulateSession() {
        var cache = new BoundedAsyncCache<Integer, String>(16, 2, 1000, () -> 0L);
        var pending = new ArrayList<CompletableFuture<String>>();
        for (int id = 0; id < 1000; id++) {
            cache.get(id, "fallback", key -> { var future = new CompletableFuture<String>(); pending.add(future); return future; });
        }
        assertEquals(2, pending.size());
        cache.clear();
        cache.get(1001, "fallback", key -> fail("Old work still counts"));
        pending.forEach(future -> future.complete("old skin"));
        assertEquals(0, cache.size());
        assertEquals(0, cache.inFlight());
        cache.get(0, "fallback", key -> CompletableFuture.completedFuture("new skin"));
        assertEquals("new skin", cache.get(0, "fallback", key -> fail("Cached")));
    }

    @Test void failedLookupRetriesOnlyAfterCooldown() {
        var now = new AtomicLong();
        var cache = new BoundedAsyncCache<Integer, String>(4, 1, 1000, now::get);
        cache.get(1, "fallback", key -> CompletableFuture.failedFuture(new IllegalStateException()));
        assertEquals("fallback", cache.get(1, "fallback", key -> fail("Too soon")));
        now.set(1000);
        cache.get(1, "fallback", key -> CompletableFuture.completedFuture("skin"));
        assertEquals("skin", cache.get(1, "fallback", key -> fail("Cached")));
    }
}
