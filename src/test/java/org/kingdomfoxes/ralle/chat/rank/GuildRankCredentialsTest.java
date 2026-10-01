package org.kingdomfoxes.ralle.chat.rank;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.client.LfgGateway;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GuildRankCredentialsTest {
    @Test void authenticatesOnlyWhenEnabledAndDoesNotOpenLfgConnection() {
        var account = UUID.randomUUID();
        var enabled = new AtomicBoolean(false);
        var calls = new ArrayList<String>();
        var gateway = (LfgGateway) Proxy.newProxyInstance(LfgGateway.class.getClassLoader(),
                new Class<?>[]{LfgGateway.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "challenge" -> CompletableFuture.completedFuture(
                                new LfgProtocol.Challenge("challenge", "server", 30, 1));
                        case "complete" -> CompletableFuture.completedFuture(new LfgProtocol.Session(
                                "token", "Bearer", 60, Instant.now().plusSeconds(60), 1,
                                new LfgProtocol.PlayerIdentity(account, "Player", null)));
                        default -> throw new AssertionError("Unexpected LFG operation: " + method.getName());
                    };
                });
        var credentials = new GuildRankCredentials(gateway, server -> {
            assertEquals("server", server);
            calls.add("proof");
            return CompletableFuture.completedFuture(null);
        }, () -> account, () -> "Player", enabled::get);
        assertThrows(java.util.concurrent.CompletionException.class, () -> credentials.get().join());
        assertTrue(calls.isEmpty());
        enabled.set(true);
        assertEquals("token", credentials.get().join());
        assertEquals(java.util.List.of("challenge", "proof", "complete"), calls);

        calls.clear();
        var pendingProof = new CompletableFuture<Void>();
        var pending = new GuildRankCredentials(gateway, server -> pendingProof,
                () -> account, () -> "Player", enabled::get).get();
        enabled.set(false);
        pendingProof.complete(null);
        assertThrows(java.util.concurrent.CompletionException.class, pending::join);
        assertEquals(java.util.List.of("challenge"), calls);
    }
}
