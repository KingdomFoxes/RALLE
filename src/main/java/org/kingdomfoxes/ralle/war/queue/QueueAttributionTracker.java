package org.kingdomfoxes.ralle.war.queue;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Bounded, session-only attribution state independent of Wynntils object identity. */
public final class QueueAttributionTracker {
    public static final long PENDING_WINDOW_MILLIS = 10_000L;
    private static final long CLEARLY_LATER_TIMER_MILLIS = 10_000L;
    public static final int MAX_PENDING = 512;
    public static final int MAX_ACTIVE = 512;

    private final LongSupplier clock;
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private LinkedHashMap<String, Active> active = new LinkedHashMap<>();
    // Retain only unexpired generations while Wynntils rebuilds its timer list.
    private final LinkedHashMap<String, Active> cached = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> captureTombstones = new LinkedHashMap<>();

    public QueueAttributionTracker(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void observe(QueueAnnouncement announcement) {
        Objects.requireNonNull(announcement, "announcement");
        long now = clock.getAsLong();
        expirePending(now);
        if (captureTombstones.containsKey(announcement.territoryName())) return;

        Active timer = active.get(announcement.territoryName());
        if (timer != null && timer.timerEndMillis() > now) {
            if (timer.senderIgn() == null) {
                active.put(announcement.territoryName(), timer.withSender(announcement.senderIgn()));
                cached.put(announcement.territoryName(), timer.withSender(announcement.senderIgn()));
            }
            return;
        }
        pending.putIfAbsent(announcement.territoryName(),
                new Pending(announcement.senderIgn(), now + PENDING_WINDOW_MILLIS));
        trimOldest(pending, MAX_PENDING);
    }

    public synchronized void reconcile(Collection<AttackTimerSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        long now = clock.getAsLong();
        expirePending(now);
        cached.entrySet().removeIf(entry -> entry.getValue().timerEndMillis() <= now);
        var next = new LinkedHashMap<String, Active>();
        var observedTerritories = new HashSet<String>();

        for (var snapshot : snapshots) {
            if (snapshot == null || snapshot.timerEndMillis() <= now) continue;
            String territory = snapshot.territoryName();
            observedTerritories.add(territory);
            Long capturedEnd = captureTombstones.get(territory);
            if (capturedEnd != null) {
                if (capturedEnd == Long.MAX_VALUE
                        || snapshot.timerEndMillis() < capturedEnd + CLEARLY_LATER_TIMER_MILLIS) continue;
                captureTombstones.remove(territory);
            }
            if (next.containsKey(territory) || next.size() >= MAX_ACTIVE) continue;

            Active previous = active.get(territory);
            if (previous == null) previous = cached.get(territory);
            Active current = previous != null && previous.timerEndMillis() > now
                    && Math.abs(previous.timerEndMillis() - snapshot.timerEndMillis()) < CLEARLY_LATER_TIMER_MILLIS
                    ? previous.withEnd(snapshot.timerEndMillis())
                    : new Active(snapshot.timerEndMillis(), null);
            Pending waiting = pending.remove(territory);
            if (current.senderIgn() == null && waiting != null && waiting.expiresAtMillis() >= now) {
                current = current.withSender(waiting.senderIgn());
            }
            next.put(territory, current);
        }
        captureTombstones.keySet().removeIf(territory -> !observedTerritories.contains(territory));
        cached.putAll(next);
        trimOldest(cached, MAX_ACTIVE);
        active = next;
    }

    /** Drops the old generation and blocks stale timer snapshots until absence or a clearly later timer. */
    public synchronized void captured(String territoryName) {
        Objects.requireNonNull(territoryName, "territoryName");
        Active removed = active.remove(territoryName);
        Active remembered = cached.remove(territoryName);
        if (removed == null) removed = remembered;
        pending.remove(territoryName);
        captureTombstones.put(territoryName,
                removed == null ? Long.MAX_VALUE : removed.timerEndMillis());
        trimOldest(captureTombstones, MAX_ACTIVE);
    }

    public synchronized Optional<String> attributionFor(String territoryName) {
        Active current = active.get(territoryName);
        return current == null || current.timerEndMillis() <= clock.getAsLong()
                ? Optional.empty() : Optional.ofNullable(current.senderIgn());
    }

    public synchronized void clear() {
        pending.clear();
        active.clear();
        cached.clear();
        captureTombstones.clear();
    }

    int pendingSize() { return pending.size(); }
    int activeSize() { return active.size(); }
    int cachedSize() { return cached.size(); }

    private void expirePending(long now) {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private static <K, V> void trimOldest(LinkedHashMap<K, V> values, int maximum) {
        while (values.size() > maximum) values.remove(values.keySet().iterator().next());
    }

    private record Pending(String senderIgn, long expiresAtMillis) {}
    private record Active(long timerEndMillis, String senderIgn) {
        Active withEnd(long end) { return new Active(end, senderIgn); }
        Active withSender(String sender) { return new Active(timerEndMillis, sender); }
    }
}
