package org.kingdomfoxes.ralle.cosmetics;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session-only positive and negative cache; call clear on disable, account/server change, or disconnect. */
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
        if (duration == 0) { entries.remove(identity.minecraftUuid()); return; }
        entries.remove(identity.minecraftUuid());
        entries.put(identity.minecraftUuid(), new Entry(identity, clock.millis() + duration));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized CosmeticIdentity get(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null) return null;
        if (clock.millis() >= entry.expiresAtMillis()) { entries.remove(uuid); return null; }
        return entry.identity();
    }

    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }

    private record Entry(CosmeticIdentity identity, long expiresAtMillis) {}
}
