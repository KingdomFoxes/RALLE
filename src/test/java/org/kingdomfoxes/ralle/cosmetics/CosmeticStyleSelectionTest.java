package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticStyleSelectionTest {
    @Test
    void onlyFoxAcceptanceChangesOwnStyleAndCredentialsStaySessionOnly() {
        UUID account = UUID.randomUUID();
        var original = new CosmeticIdentity(account, Set.of("contributor"), "contributor-green", 1);
        var cache = new NameplateDirectorySession(uuids -> CompletableFuture.completedFuture(
                new CosmeticLookupJson.Lookup(List.of(original), Duration.ofSeconds(30))),
                () -> true, () -> "play.wynncraft.com", () -> account, Clock.systemUTC());
        cache.tick(List.of(account));
        var gateway = new FakeGateway(account);
        var selection = new CosmeticStyleSelection(gateway, ignored -> CompletableFuture.completedFuture(null),
                cache, () -> true, () -> account, () -> "ExamplePlayer");
        assertThrows(CompletionException.class, () -> selection.select("admin-red").join());
        assertEquals(0, gateway.challenges);
        var failed = selection.select("contributor-blue");
        gateway.mutation.completeExceptionally(new IllegalStateException("offline"));
        assertThrows(CompletionException.class, failed::join);
        assertEquals("contributor-green", cache.cached(account).selectedStyleId());

        gateway.mutation = new CompletableFuture<>();
        var success = selection.select("contributor-blue");
        gateway.mutation.complete(new CosmeticIdentity(account, Set.of("contributor"), "contributor-blue", 2));
        assertEquals("contributor-blue", success.join().selectedStyleId());
        assertEquals("contributor-blue", cache.cached(account).selectedStyleId());
        assertEquals(2, gateway.challenges); // Reproves ownership after a failed mutation.
        selection.clear();
        cache.clear();
        assertNull(cache.cached(account));
    }

    @Test
    void rejectsASelectionResponseForAnotherAccount() {
        UUID account = UUID.randomUUID();
        var original = new CosmeticIdentity(account, Set.of("supporter"), "supporter-gold", 1);
        var cache = new NameplateDirectorySession(uuids -> CompletableFuture.completedFuture(
                new CosmeticLookupJson.Lookup(List.of(original), Duration.ofSeconds(30))),
                () -> true, () -> "play.wynncraft.com", () -> account, Clock.systemUTC());
        cache.tick(List.of(account));
        var gateway = new FakeGateway(account);
        var selection = new CosmeticStyleSelection(gateway, ignored -> CompletableFuture.completedFuture(null),
                cache, () -> true, () -> account, () -> "ExamplePlayer");
        var request = selection.select("supporter-gold");
        gateway.mutation.complete(new CosmeticIdentity(UUID.randomUUID(), Set.of("supporter"), "supporter-gold", 2));
        assertThrows(CompletionException.class, request::join);
        assertEquals(1, cache.cached(account).revision());
    }

    @Test void noneWaitsForFoxAcceptanceAndCanBeReplacedWithAColor() {
        UUID account = UUID.randomUUID();
        var original = new CosmeticIdentity(account, Set.of("supporter"), "supporter-gold", 1);
        var cache = new NameplateDirectorySession(uuids -> CompletableFuture.completedFuture(
                new CosmeticLookupJson.Lookup(List.of(original), Duration.ofSeconds(30))),
                () -> true, () -> "play.wynncraft.com", () -> account, Clock.systemUTC());
        cache.tick(List.of(account));
        var gateway = new FakeGateway(account);
        var selection = new CosmeticStyleSelection(gateway, ignored -> CompletableFuture.completedFuture(null),
                cache, () -> true, () -> account, () -> "ExamplePlayer");
        var failed = selection.select(null);
        assertNull(gateway.requestedStyle);
        assertEquals(original, cache.cached(account));
        assertThrows(CompletionException.class, () -> selection.select("supporter-gold").join());
        gateway.mutation.completeExceptionally(new IllegalStateException("offline"));
        assertThrows(CompletionException.class, failed::join);
        assertEquals(original, cache.cached(account));

        gateway.mutation = new CompletableFuture<>();
        var cleared = selection.select(null);
        gateway.mutation.complete(new CosmeticIdentity(account, Set.of("supporter"), null, 2));
        assertNull(cleared.join().selectedStyle());
        assertNull(cache.cached(account).selectedStyle());
        assertTrue(cache.cached(account).hasNameplateAccess());

        gateway.mutation = new CompletableFuture<>();
        var restored = selection.select("supporter-gold");
        gateway.mutation.complete(new CosmeticIdentity(account, Set.of("supporter"), "supporter-gold", 3));
        assertEquals("supporter-gold", restored.join().selectedStyleId());
        assertEquals("supporter-gold", cache.cached(account).selectedStyleId());
        assertEquals(2, gateway.challenges);
    }

    @Test void noneRequiresACurrentCosmeticGrant() {
        UUID account = UUID.randomUUID();
        var cache = new NameplateDirectorySession(uuids -> CompletableFuture.completedFuture(
                new CosmeticLookupJson.Lookup(List.of(CosmeticIdentity.neutral(account, 1)), Duration.ofSeconds(30))),
                () -> true, () -> "play.wynncraft.com", () -> account, Clock.systemUTC());
        cache.tick(List.of(account));
        var gateway = new FakeGateway(account);
        var selection = new CosmeticStyleSelection(gateway, ignored -> CompletableFuture.completedFuture(null),
                cache, () -> true, () -> account, () -> "ExamplePlayer");
        assertThrows(CompletionException.class, () -> selection.select(null).join());
        assertEquals(0, gateway.challenges);
    }

    private static final class FakeGateway implements CosmeticSelfGateway {
        final UUID account;
        int challenges;
        String requestedStyle;
        CompletableFuture<CosmeticIdentity> mutation = new CompletableFuture<>();
        FakeGateway(UUID account) { this.account = account; }
        @Override public CompletableFuture<Challenge> challenge(UUID account, String ign) {
            challenges++;
            return CompletableFuture.completedFuture(new Challenge("challenge", "server"));
        }
        @Override public CompletableFuture<Session> complete(String challengeId) {
            return CompletableFuture.completedFuture(new Session("memory-only", account,
                    System.currentTimeMillis() + 60_000));
        }
        @Override public CompletableFuture<CosmeticIdentity> select(String bearer, String styleId) {
            requestedStyle = styleId;
            return mutation;
        }
    }
}
