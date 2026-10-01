package org.kingdomfoxes.ralle.ui;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Bounded access-order cache. Pending work counts against the limit even across clear(). */
public final class BoundedAsyncCache<K, V> {
    private final int capacity;
    private final int concurrency;
    private final long retryMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private int inFlight;

    public BoundedAsyncCache(int capacity, int concurrency, long retryMillis, LongSupplier clock) {
        if (capacity < 1 || concurrency < 1 || concurrency > capacity || retryMillis < 1) {
            throw new IllegalArgumentException("Invalid cache bounds");
        }
        this.capacity = capacity;
        this.concurrency = concurrency;
        this.retryMillis = retryMillis;
        this.clock = clock;
    }

    public V get(K key, V fallback, Function<K, CompletableFuture<V>> loader) {
        Entry<V> entry;
        synchronized (this) {
            entry = entries.get(key);
            if (entry != null) {
                if (entry.value != null) return entry.value;
                if (entry.pending || clock.getAsLong() < entry.retryAt) return fallback;
            }
            if (inFlight >= concurrency) return fallback;
            if (entry == null) {
                if (entries.size() >= capacity) {
                    var iterator = entries.entrySet().iterator();
                    while (iterator.hasNext()) {
                        if (!iterator.next().getValue().pending) { iterator.remove(); break; }
                    }
                    if (entries.size() >= capacity) return fallback;
                }
                entry = new Entry<>();
                entries.put(key, entry);
            }
            entry.pending = true;
            inFlight++;
        }
        var requested = entry;
        try {
            loader.apply(key).whenComplete((value, failure) -> finish(key, requested, value, failure));
        } catch (RuntimeException failure) {
            finish(key, requested, null, failure);
        }
        return fallback;
    }

    private synchronized void finish(K key, Entry<V> entry, V value, Throwable failure) {
        inFlight--;
        // Identity check prevents a late completion from restoring a discarded session.
        if (entries.get(key) != entry) return;
        entry.pending = false;
        entry.value = failure == null ? value : null;
        entry.retryAt = clock.getAsLong() + retryMillis;
    }

    public synchronized void clear() { entries.clear(); }
    synchronized int size() { return entries.size(); }
    synchronized int inFlight() { return inFlight; }

    private static final class Entry<V> {
        private boolean pending;
        private long retryAt;
        private V value;
    }
}
