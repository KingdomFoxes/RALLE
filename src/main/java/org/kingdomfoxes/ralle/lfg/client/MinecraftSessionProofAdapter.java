package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

/** Executes Mojang's session join away from Minecraft's render thread. */
public final class MinecraftSessionProofAdapter implements MinecraftSessionProof {
    private static final SessionProofQueue AUTHENTICATIONS = new SessionProofQueue();
    private final Minecraft minecraft;

    public MinecraftSessionProofAdapter(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public <T> CompletableFuture<T> authenticate(String serverId,
            java.util.function.Supplier<CompletableFuture<T>> complete) {
        return AUTHENTICATIONS.submit(() -> prove(serverId).thenCompose(ignored -> complete.get()));
    }

    @Override
    public CompletableFuture<Void> prove(String serverId) {
        var user = minecraft.getUser();
        return CompletableFuture.runAsync(() -> {
            try {
                minecraft.services().sessionService()
                        .joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            } catch (com.mojang.authlib.exceptions.AuthenticationException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        });
    }
}
