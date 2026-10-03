package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.lfg.client.MinecraftSessionProof;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Explicit own-color selection; the Fox response alone updates local presentation. */
public final class CosmeticStyleSelection {
    private final CosmeticSelfGateway gateway;
    private final MinecraftSessionProof proof;
    private final NameplateDirectorySession directory;
    private final BooleanSupplier enabled;
    private final Supplier<UUID> account;
    private final Supplier<String> ign;
    private CosmeticSelfGateway.Session session;
    private CompletableFuture<CosmeticIdentity> pending;
    private long generation;

    public CosmeticStyleSelection(CosmeticSelfGateway gateway, MinecraftSessionProof proof,
                                  NameplateDirectorySession directory, BooleanSupplier enabled,
                                  Supplier<UUID> account, Supplier<String> ign) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.proof = Objects.requireNonNull(proof, "proof");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.account = Objects.requireNonNull(account, "account");
        this.ign = Objects.requireNonNull(ign, "ign");
    }

    public synchronized CompletableFuture<CosmeticIdentity> select(String styleId) {
        UUID player = account.get();
        CosmeticIdentity current = player == null ? null : directory.cached(player);
        if (!enabled.getAsBoolean() || current == null || !directory.settingsAllowed()
                || styleId != null && !NameplateStyle.allowed(styleId, current.grants()))
            return CompletableFuture.failedFuture(new IllegalStateException("Style is unavailable"));
        if (pending != null && !pending.isDone())
            return CompletableFuture.failedFuture(new IllegalStateException("Style selection already pending"));
        long requestGeneration = generation;
        CompletableFuture<CosmeticSelfGateway.Session> auth = session != null
                && session.account().equals(player) && session.expiresAtMillis() > System.currentTimeMillis() + 10_000
                ? CompletableFuture.completedFuture(session) : gateway.challenge(player, ign.get())
                    .thenCompose(challenge -> proof.authenticate(challenge.serverId(),
                            () -> gateway.complete(challenge.id())));
        pending = auth.thenCompose(verified -> {
            synchronized (this) {
                if (generation != requestGeneration || !player.equals(account.get()) || !enabled.getAsBoolean()
                        || !player.equals(verified.account()))
                    return CompletableFuture.failedFuture(new IllegalStateException("Cosmetic session changed"));
                var latest = directory.cached(player);
                if (!directory.settingsAllowed() || latest == null
                        || styleId != null && !NameplateStyle.allowed(styleId, latest.grants()))
                    return CompletableFuture.failedFuture(new IllegalStateException("Style is unavailable"));
                session = verified;
            }
            return gateway.select(verified.token(), styleId).whenComplete((ignored, failure) -> {
                if (failure != null) synchronized (this) {
                    if (generation == requestGeneration) session = null;
                }
            });
        }).thenApply(accepted -> {
            synchronized (this) {
                if (generation != requestGeneration || !player.equals(account.get())
                        || !player.equals(accepted.minecraftUuid()) || !enabled.getAsBoolean())
                    throw new IllegalStateException("Cosmetic session changed");
                directory.acceptSelf(accepted);
            }
            return accepted;
        });
        return pending;
    }

    public synchronized void clear() {
        generation++;
        session = null;
        pending = null;
    }
}
