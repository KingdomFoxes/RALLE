package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SessionProofQueueTest {
    @Test void waitsForBackendVerificationBeforeRegisteringNextProof() throws Exception {
        var queue = new SessionProofQueue();
        var verifying = new CompletableFuture<String>();
        var started = new CompletableFuture<Void>();
        var firstVerified = new AtomicBoolean();
        var first = queue.submit(() -> {
            started.complete(null);
            return verifying.thenApply(value -> { firstVerified.set(true); return value; });
        });
        started.get(5, TimeUnit.SECONDS);
        var second = queue.submit(() -> {
            assertTrue(firstVerified.get(), "Second proof overwrote the unverified first proof");
            return CompletableFuture.completedFuture("second");
        });
        assertFalse(second.isDone());
        verifying.complete("first");
        assertEquals("first", first.get(5, TimeUnit.SECONDS));
        assertEquals("second", second.get(5, TimeUnit.SECONDS));
    }

    @Test void failedVerificationDoesNotBlockNextAuthentication() throws Exception {
        var queue = new SessionProofQueue();
        var failed = queue.submit(() -> CompletableFuture.failedFuture(new IllegalStateException("rejected")));
        var next = queue.submit(() -> CompletableFuture.completedFuture("recovered"));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertEquals("recovered", next.get(5, TimeUnit.SECONDS));
    }
}
