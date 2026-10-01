package org.kingdomfoxes.ralle.lfg.client;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.LoggerFactory;

/** One reentrant state lock for service, store and join transitions. External callbacks
 * are queued in transition order and dispatched only after the outermost lock is released.
 * A competing transition never waits for callback delivery (listeners may call back in).
 */
final class LfgStateAccess {
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<Runnable> effects = new ArrayDeque<>();
    private boolean dispatching;

    Scope enter() {
        lock.lock();
        return new Scope();
    }

    void afterUnlock(Runnable effect) {
        if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("State lock required");
        effects.addLast(effect);
    }

    <T> void complete(CompletableFuture<T> future, T value) {
        afterUnlock(() -> future.complete(value));
    }

    void fail(CompletableFuture<?> future, Throwable failure) {
        afterUnlock(() -> future.completeExceptionally(failure));
    }

    final class Scope implements AutoCloseable {
        @Override public void close() {
            boolean drain = lock.getHoldCount() == 1 && !dispatching && !effects.isEmpty();
            if (drain) dispatching = true;
            lock.unlock();
            if (!drain) return;
            while (true) {
                Runnable effect;
                lock.lock();
                try {
                    effect = effects.pollFirst();
                    if (effect == null) { dispatching = false; return; }
                } finally {
                    lock.unlock();
                }
                try {
                    effect.run();
                } catch (Throwable failure) {
                    LoggerFactory.getLogger(LfgStateAccess.class).error("Raid LFG callback failed", failure);
                }
            }
        }
    }
}
