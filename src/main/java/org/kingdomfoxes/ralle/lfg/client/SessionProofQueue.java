package org.kingdomfoxes.ralle.lfg.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Prevents another RALLE proof from replacing a join while Fox verifies it. */
final class SessionProofQueue {
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    synchronized <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> operation) {
        var result = tail.thenComposeAsync(ignored -> operation.get());
        tail = result.handle((ignored, failure) -> null);
        return result;
    }
}
