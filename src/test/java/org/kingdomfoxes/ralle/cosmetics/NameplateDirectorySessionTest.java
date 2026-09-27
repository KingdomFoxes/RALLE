package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NameplateDirectorySessionTest {
    private final UUID account = UUID.randomUUID(), visible = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicReference<String> host = new AtomicReference<>("play.wynncraft.com");
    private final AtomicReference<UUID> signedIn = new AtomicReference<>();

    @Test
    void disabledOffServerAndAccountChangesNeverExposeStaleMetadata() {
        var directory = new FakeDirectory();
        var session = session(directory);
        session.tick(List.of(visible));
        assertEquals(0, directory.calls);
        enabled.set(true);
        session.tick(List.of(visible));
        assertEquals(0, directory.calls);
        signedIn.set(account);
        session.tick(List.of(visible));
        assertEquals(1, directory.calls);
        directory.reply.complete(reply(visible, "supporter-gold"));
        assertEquals("supporter-gold", session.cached(visible).selectedStyleId());
        enabled.set(false);
        assertNull(session.cached(visible));
        session.tick(List.of(visible));
        enabled.set(true);
        host.set("evilwynncraft.com");
        session.tick(List.of(visible));
        assertEquals(1, directory.calls);
        host.set("play.wynncraft.com");
        session.tick(List.of(visible));
        assertEquals(2, directory.calls);
        signedIn.set(UUID.randomUUID());
        assertNull(session.cached(visible));
    }

    @Test
    void failureBacksOffAndLateReplyCannotRestoreRevokedData() {
        enabled.set(true);
        signedIn.set(account);
        var directory = new FakeDirectory();
        var session = session(directory);
        session.tick(List.of(visible));
        var late = directory.reply;
        session.clear();
        late.complete(reply(visible, "supporter-gold"));
        assertNull(session.cached(visible));
        session.tick(List.of(visible));
        directory.reply.completeExceptionally(new RuntimeException("offline"));
        session.tick(List.of(visible));
        assertEquals(2, directory.calls);
        clock.advance(2_000);
        session.tick(List.of(visible));
        assertEquals(3, directory.calls);
        directory.reply.complete(reply(visible, null));
        assertNull(session.cached(visible).selectedStyle());
        session.tick(List.of(visible));
        assertEquals(3, directory.calls);
        clock.advance(30_000);
        session.tick(List.of(visible));
        assertEquals(4, directory.calls);
    }

    @Test
    void successfulBatchesRespectFoxLookupRateLimit() {
        enabled.set(true);
        signedIn.set(account);
        var directory = new FakeDirectory();
        var session = session(directory);
        UUID second = UUID.randomUUID();
        session.tick(List.of(visible));
        directory.reply.complete(reply(visible, "supporter-gold"));
        session.tick(List.of(visible, second));
        assertEquals(1, directory.calls);
        clock.advance(1_100);
        session.tick(List.of(visible, second));
        assertEquals(2, directory.calls);
    }

    @Test
    void olderLookupCannotOverwriteAcceptedOwnStyle() {
        enabled.set(true);
        signedIn.set(account);
        var directory = new FakeDirectory();
        var session = session(directory);
        session.tick(List.of(account));
        session.acceptSelf(new CosmeticIdentity(account, Set.of("contributor"), "contributor-blue", 3));
        directory.reply.complete(new CosmeticLookupJson.Lookup(List.of(
                new CosmeticIdentity(account, Set.of("contributor"), "contributor-green", 2)),
                Duration.ofSeconds(30)));
        assertEquals("contributor-blue", session.cached(account).selectedStyleId());
    }

    private NameplateDirectorySession session(FakeDirectory directory) {
        return new NameplateDirectorySession(directory, enabled::get, host::get, signedIn::get, clock);
    }

    private CosmeticLookupJson.Lookup reply(UUID uuid, String style) {
        var identity = style == null ? CosmeticIdentity.neutral(uuid, 2)
                : new CosmeticIdentity(uuid, Set.of("supporter"), style, 1);
        return new CosmeticLookupJson.Lookup(List.of(identity), Duration.ofSeconds(30));
    }

    private static final class FakeDirectory implements NameplateDirectory {
        int calls;
        CompletableFuture<CosmeticLookupJson.Lookup> reply;
        @Override public CompletableFuture<CosmeticLookupJson.Lookup> lookup(List<UUID> uuids) {
            calls++;
            reply = new CompletableFuture<>();
            return reply;
        }
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
