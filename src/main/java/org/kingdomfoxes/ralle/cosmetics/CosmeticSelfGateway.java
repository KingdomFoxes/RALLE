package org.kingdomfoxes.ralle.cosmetics;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fox identity proof and self-style mutation. Credentials never enter settings. */
public interface CosmeticSelfGateway {
    CompletableFuture<Challenge> challenge(UUID account, String ign);
    CompletableFuture<Session> complete(String challengeId);
    CompletableFuture<CosmeticIdentity> select(String bearer, String styleId);

    record Challenge(String id, String serverId) {}
    record Session(String token, UUID account, long expiresAtMillis) {}
}
