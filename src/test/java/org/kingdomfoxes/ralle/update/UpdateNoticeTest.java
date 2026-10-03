package org.kingdomfoxes.ralle.update;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class UpdateNoticeTest {
    private static final ModrinthRelease RELEASE = new ModrinthRelease("ABCDEFGH", "RALLE 0.1.9", "0.1.9");

    @Test
    void checksOnlyAfterLoginAndDeliversOnceOnClientTick() {
        var future = new CompletableFuture<Optional<ModrinthRelease>>();
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> { calls.incrementAndGet(); return future; }, () -> 0);
        assertTrue(notice.tick().isEmpty());
        assertEquals(0, calls.get());
        notice.joined();
        assertTrue(notice.tick().isEmpty());
        assertTrue(notice.tick().isEmpty());
        assertEquals(1, calls.get());
        future.complete(Optional.of(RELEASE));
        assertEquals(RELEASE, notice.tick().orElseThrow());
        for (int i = 0; i < 100; i++) assertTrue(notice.tick().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void disconnectedRepliesStayQuietAndRapidReconnectReusesPublicResult() {
        var future = new CompletableFuture<Optional<ModrinthRelease>>();
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> { calls.incrementAndGet(); return future; }, () -> 0);
        notice.joined();
        notice.tick();
        notice.disconnected();
        future.complete(Optional.of(RELEASE));
        assertTrue(notice.tick().isEmpty());
        notice.joined();
        assertEquals(RELEASE, notice.tick().orElseThrow());
        assertEquals(1, calls.get());
    }

    @Test
    void repeatedLoginUsesOneMinuteCacheThenRefreshes() {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.of(RELEASE));
        }, clock::get);
        notice.joined();
        notice.tick();
        assertEquals(RELEASE, notice.tick().orElseThrow());
        notice.disconnected();
        clock.set(59_999);
        notice.joined();
        assertEquals(RELEASE, notice.tick().orElseThrow());
        assertTrue(notice.tick().isEmpty());
        assertEquals(1, calls.get());
        notice.disconnected();
        clock.set(60_000);
        notice.joined();
        notice.tick();
        assertEquals(2, calls.get());
        assertEquals(RELEASE, notice.tick().orElseThrow());
    }

    @Test
    void asynchronousFailuresRemainSilentAndDoNotRetryUntilAnotherLoginAfterCooldown() {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("offline / malformed / HTTP 404"));
        }, clock::get);
        notice.joined();
        notice.tick();
        assertTrue(notice.tick().isEmpty());
        clock.set(60_000);
        for (int i = 0; i < 100; i++) assertTrue(notice.tick().isEmpty());
        assertEquals(1, calls.get());
        notice.joined();
        notice.tick();
        assertEquals(2, calls.get());
    }

    @Test
    void longDisconnectDiscardsExpiredPendingResultAndMakesFreshLoginCheck() {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(calls.get() == 1 ? Optional.of(RELEASE) : Optional.empty());
        }, clock::get);
        notice.joined();
        notice.tick();
        notice.disconnected();
        clock.set(300_000);
        notice.joined();
        assertTrue(notice.tick().isEmpty());
        assertEquals(2, calls.get());
        assertTrue(notice.tick().isEmpty());
    }

    @Test
    void synchronousFailuresAreAlsoBoundedAndSafe() {
        var calls = new AtomicInteger();
        var notice = new UpdateNotice(() -> { calls.incrementAndGet(); throw new IllegalStateException("closed"); }, () -> 0);
        notice.joined();
        assertTrue(notice.tick().isEmpty());
        notice.joined();
        assertTrue(notice.tick().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void upToDateLoginHasNoNotice() {
        var notice = new UpdateNotice(() -> CompletableFuture.completedFuture(Optional.empty()), () -> 0);
        notice.joined();
        notice.tick();
        assertTrue(notice.tick().isEmpty());
    }
}
