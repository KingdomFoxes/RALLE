package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.function.LongSupplier;

/** Lazily samples the graph once per client tick; attack timers always remain live. */
final class TickCachedTerritorySource implements TerritorySnapshotSource {
    private final TerritorySnapshotSource delegate;
    private final LongSupplier tick;
    private long sampledTick;
    private TerritorySnapshot snapshot;

    TickCachedTerritorySource(TerritorySnapshotSource delegate, LongSupplier tick) {
        this.delegate = delegate;
        this.tick = tick;
    }

    @Override public TerritorySnapshot snapshot() {
        long current = tick.getAsLong();
        if (snapshot == null || sampledTick != current) {
            snapshot = delegate.snapshot();
            sampledTick = current;
        }
        return snapshot;
    }

    @Override public boolean hasActiveAttackTimer(String territoryName) {
        return delegate.hasActiveAttackTimer(territoryName);
    }
}
