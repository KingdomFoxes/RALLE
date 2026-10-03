package org.kingdomfoxes.ralle.cosmetics;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session-only presentation cache; TTL schedules refresh, not removal of the last accepted appearance. */
public final class SessionCosmeticCache {
    public static final int MAX_ENTRIES = 256;
    public static final Duration MAX_AGE = Duration.ofSeconds(30);
    private final Clock clock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public SessionCosmeticCache(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public synchronized void put(CosmeticIdentity identity, Duration serverTtl) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(serverTtl, "serverTtl");
        long duration = Math.max(0, Math.min(MAX_AGE.toMillis(), serverTtl.toMillis()));
        entries.remove(identity.minecraftUuid());
        entries.put(identity.minecraftUuid(), new Entry(identity, clock.millis() + duration));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized CosmeticIdentity get(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null ? null : entry.identity();
    }

    /** Keep presentation stable while a tick-driven refresh is pending or backing off. */
    public synchronized boolean needsRefresh(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null || clock.millis() >= entry.expiresAtMillis();
    }

    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }

    private record Entry(CosmeticIdentity identity, long expiresAtMillis) {}
}
