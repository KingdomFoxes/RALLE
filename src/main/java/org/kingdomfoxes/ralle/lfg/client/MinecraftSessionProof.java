package org.kingdomfoxes.ralle.lfg.client;

import java.util.concurrent.CompletableFuture;

/** Testable boundary around Minecraft's authenticated session join call. */
@FunctionalInterface
public interface MinecraftSessionProof {
    CompletableFuture<Void> prove(String serverId);
}
