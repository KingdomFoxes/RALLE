package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.client.WynncraftHost;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Tick-driven lookup scheduler. No HTTP occurs in render paths or while the feature is disabled. */
public final class NameplateDirectorySession {
    private final NameplateDirectory directory;
    private final SessionCosmeticCache cache;
    private final BooleanSupplier enabled;
    private final Supplier<String> serverHost;
    private final Supplier<UUID> accountId;
    private final Clock clock;
    private String sessionHost = "";
    private UUID sessionAccount;
    private long generation;
    private long presentationRevision;
    private long retryAfterMillis;
    private int failures;
    private boolean pending;

    public NameplateDirectorySession(NameplateDirectory directory, BooleanSupplier enabled,
                                     Supplier<String> serverHost, Supplier<UUID> accountId, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.serverHost = Objects.requireNonNull(serverHost, "serverHost");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cache = new SessionCosmeticCache(clock);
    }

    public synchronized CosmeticIdentity cached(UUID uuid) {
        if (!active() || !WynncraftHost.normalize(serverHost.get()).equals(sessionHost)
                || !Objects.equals(accountId.get(), sessionAccount)) return null;
        return cache.get(uuid);
    }

    /** Call from a client tick with visible player UUIDs, never from a render callback. */
    public synchronized void tick(Collection<UUID> visiblePlayers) {
        if (!active()) { clear(); return; }
        String host = WynncraftHost.normalize(serverHost.get());
        UUID account = accountId.get();
        if (!host.equals(sessionHost) || !Objects.equals(account, sessionAccount)) {
            clear();
            sessionHost = host;
            sessionAccount = account;
        }
        if (pending || clock.millis() < retryAfterMillis || visiblePlayers == null) return;
        var missing = new LinkedHashSet<UUID>();
        for (UUID uuid : visiblePlayers) {
            if (uuid != null && cache.needsRefresh(uuid)) missing.add(uuid);
            if (missing.size() == CosmeticLookupJson.MAX_BATCH) break;
        }
        if (missing.isEmpty()) return;
        List<UUID> batch = List.copyOf(missing);
        long requestGeneration = generation;
        pending = true;
        try {
            directory.lookup(batch).whenComplete((result, failure) -> accept(requestGeneration, batch, result, failure));
        } catch (RuntimeException failure) {
            accept(requestGeneration, batch, null, failure);
        }
    }

    public synchronized void clear() {
        if (cache.size() == 0 && !pending && sessionHost.isEmpty() && sessionAccount == null) return;
        generation++;
        presentationRevision++;
        cache.clear();
        pending = false;
        retryAfterMillis = 0;
        failures = 0;
        sessionHost = "";
        sessionAccount = null;
    }

    /** Install only Fox's accepted self-style response for the currently proved account. */
    public synchronized void acceptSelf(CosmeticIdentity identity) {
        if (identity != null && active() && Objects.equals(accountId.get(), identity.minecraftUuid())
                && Objects.equals(accountId.get(), sessionAccount)
                && WynncraftHost.normalize(serverHost.get()).equals(sessionHost)) {
            CosmeticIdentity current = cache.get(identity.minecraftUuid());
            if (current == null || identity.revision() > current.revision()
                    || identity.revision() == current.revision() && identity.equals(current)) {
                cache.put(identity, SessionCosmeticCache.MAX_AGE);
                if (!identity.equals(current)) presentationRevision++;
            }
        }
    }

    public synchronized long presentationRevision() { return presentationRevision; }

    private boolean active() {
        return enabled.getAsBoolean() && accountId.get() != null && WynncraftHost.matches(serverHost.get());
    }

    private synchronized void accept(long requestGeneration, List<UUID> requested,
                                     CosmeticLookupJson.Lookup result, Throwable failure) {
        if (requestGeneration != generation || !active()) return;
        pending = false;
        if (failure != null || result == null || result.players().size() != requested.size()) {
            backoff();
            return;
        }
        for (int i = 0; i < requested.size(); i++) {
            if (!requested.get(i).equals(result.players().get(i).minecraftUuid())) { backoff(); return; }
        }
        boolean changed = false;
        for (CosmeticIdentity identity : result.players()) {
            CosmeticIdentity current = cache.get(identity.minecraftUuid());
            if (current == null || identity.revision() > current.revision()
                    || identity.revision() == current.revision() && identity.equals(current)) {
                cache.put(identity, result.ttl());
                changed |= !identity.equals(current);
            }
        }
        if (changed) presentationRevision++;
        failures = 0;
        // Fox allows 60 lookups per minute per IP. Keep crowded-world batches below that rate.
        retryAfterMillis = clock.millis() + 1_100;
    }

    private void backoff() {
        failures = Math.min(6, failures + 1);
        retryAfterMillis = clock.millis() + Math.min(Duration.ofMinutes(1).toMillis(), 1000L << failures);
    }
}
