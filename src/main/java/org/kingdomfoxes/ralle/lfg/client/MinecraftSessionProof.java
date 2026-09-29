package org.kingdomfoxes.ralle.lfg.client;

import java.util.concurrent.CompletableFuture;

/** Testable boundary around Minecraft's authenticated session join call. */
@FunctionalInterface
public interface MinecraftSessionProof {
    CompletableFuture<Void> prove(String serverId);

    /** Keep proof registration and backend verification together when sharing a Minecraft session. */
    default <T> CompletableFuture<T> authenticate(String serverId,
            java.util.function.Supplier<CompletableFuture<T>> complete) {
        return prove(serverId).thenCompose(ignored -> complete.get());
    }
}
