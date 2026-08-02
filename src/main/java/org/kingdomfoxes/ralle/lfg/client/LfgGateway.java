package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Transport boundary for the public Fox protocol-v1 client API. */
public interface LfgGateway {
    CompletableFuture<LfgProtocol.Status> status();
    CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign);
    CompletableFuture<LfgProtocol.Session> complete(String challengeId);
    CompletableFuture<LfgProtocol.Snapshot> snapshot(String bearerToken);
    CompletableFuture<LfgProtocol.Mutation> create(String bearerToken, LfgProtocol.RaidType raid,
                                                   LfgProtocol.Region region, String note, UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> join(String bearerToken, UUID lobbyId, UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> leave(String bearerToken, UUID lobbyId, UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> disband(String bearerToken, UUID lobbyId, UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> kick(String bearerToken, UUID lobbyId, UUID targetId,
                                                 UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> setLocked(String bearerToken, UUID lobbyId, boolean locked,
                                                      UUID idempotencyKey);
    CompletableFuture<LfgProtocol.Mutation> ping(String bearerToken, UUID lobbyId, UUID idempotencyKey);
    CompletableFuture<LiveConnection> connectLive(String bearerToken, LiveListener listener);

    interface LiveConnection extends AutoCloseable {
        @Override void close();
    }

    interface LiveListener {
        void onFrame(LfgProtocol.LiveFrame frame);
        void onClosed(int statusCode, String reason);
        void onFailure(Throwable failure);
    }
}
