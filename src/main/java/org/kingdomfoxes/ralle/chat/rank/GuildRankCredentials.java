package org.kingdomfoxes.ralle.chat.rank;

import org.kingdomfoxes.ralle.lfg.client.LfgGateway;
import org.kingdomfoxes.ralle.lfg.client.MinecraftSessionProof;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** One memory-only credential per explicit or scheduled rank refresh; no live LFG connection. */
public final class GuildRankCredentials implements Supplier<CompletableFuture<String>> {
    private final LfgGateway gateway;
    private final MinecraftSessionProof proof;
    private final Supplier<UUID> account;
    private final Supplier<String> ign;
    private final BooleanSupplier enabled;

    public GuildRankCredentials(LfgGateway gateway, MinecraftSessionProof proof,
                                Supplier<UUID> account, Supplier<String> ign, BooleanSupplier enabled) {
        this.gateway = gateway;
        this.proof = proof;
        this.account = account;
        this.ign = ign;
        this.enabled = enabled;
    }

    @Override public CompletableFuture<String> get() {
        UUID expected = account.get();
        if (!enabled.getAsBoolean() || expected == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Fox ranks are inactive"));
        }
        return gateway.challenge(expected, ign.get()).thenCompose(challenge -> {
            requireCurrent(expected);
            requireProtocol(challenge.protocolVersion());
            return proof.authenticate(challenge.serverId(), () -> {
                requireCurrent(expected);
                return gateway.complete(challenge.challengeId());
            });
        }).thenApply(session -> {
            requireCurrent(expected);
            requireProtocol(session.protocolVersion());
            if (!expected.equals(session.player().minecraftUuid())) {
                throw new IllegalStateException("Fox rank credential belongs to another account");
            }
            return session.accessToken();
        });
    }

    private void requireCurrent(UUID expected) {
        if (!enabled.getAsBoolean() || !expected.equals(account.get())) {
            throw new IllegalStateException("Fox rank refresh is no longer active");
        }
    }

    private static void requireProtocol(int version) {
        if (version != LfgProtocol.VERSION) {
            throw new IllegalStateException("Fox rank authentication protocol is incompatible");
        }
    }
}
