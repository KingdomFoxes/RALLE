package org.kingdomfoxes.ralle.war.hqdistance;

/** Optional-integration boundary supplying current territory data without owning or polling it. */
public interface TerritorySnapshotSource {
    TerritorySnapshot snapshot();

    boolean hasActiveAttackTimer(String territoryName);
}
