package org.kingdomfoxes.ralle.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FoxGuildAccessTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void staysLockedUntilLoginLookupConfirmsFoxAndClearsOnDisconnect() {
        var result = new CompletableFuture<Boolean>();
        var calls = new AtomicInteger();
        var access = new FoxGuildAccess(uuid -> {
            assertEquals(PLAYER, uuid);
            calls.incrementAndGet();
            return result;
        }, () -> 0);
        assertFalse(access.allowed());
        access.tick();
        assertEquals(0, calls.get());
        access.joined("play.wynncraft.com", PLAYER);
        access.tick();
        assertFalse(access.allowed());
        result.complete(true);
        access.tick();
        assertTrue(access.allowed());
        for (int tick = 0; tick < 100; tick++) access.tick();
        assertEquals(1, calls.get());
        access.clear();
        assertFalse(access.allowed());
    }

    @Test
    void oldConnectionReplyCannotUnlockNewSessionAndOffWynncraftIsInert() {
        var old = new CompletableFuture<Boolean>();
        var next = new CompletableFuture<Boolean>();
        var calls = new AtomicInteger();
        var access = new FoxGuildAccess(uuid -> calls.incrementAndGet() == 1 ? old : next, () -> 0);
        access.joined("play.wynncraft.com", PLAYER);
        access.tick();
        access.joined("play.wynncraft.com", UUID.randomUUID());
        old.complete(true);
        access.tick();
        assertFalse(access.allowed());
        next.complete(false);
        access.tick();
        assertFalse(access.allowed());
        access.joined("example.com", PLAYER);
        access.tick();
        assertEquals(2, calls.get());
    }

    @Test
    void failureRetriesAreDelayedBoundedAndNeverUnlockAccess() {
        var calls = new AtomicInteger();
        var clock = new AtomicLong();
        var access = new FoxGuildAccess(uuid -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("API offline"));
        }, clock::get);
        access.joined("play.wynncraft.com", PLAYER);
        access.tick();
        access.tick();
        clock.set(29_999);
        access.tick();
        assertEquals(1, calls.get());
        clock.set(30_000);
        access.tick();
        access.tick();
        clock.set(60_000);
        access.tick();
        access.tick();
        clock.set(1_000_000);
        access.tick();
        assertEquals(3, calls.get());
        assertFalse(access.allowed());
        access.joined("play.wynncraft.com", PLAYER);
        access.tick();
        assertEquals(4, calls.get());
    }

    @Test
    void synchronousFailureAlsoKeepsRetryBudget() {
        var calls = new AtomicInteger();
        var clock = new AtomicLong();
        var access = new FoxGuildAccess(uuid -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Transport stopped");
        }, clock::get);
        access.joined("play.wynncraft.com", PLAYER);
        for (int tick = 0; tick < 10; tick++) {
            access.tick();
            clock.addAndGet(30_000);
        }
        assertEquals(3, calls.get());
        assertFalse(access.allowed());
    }
}
