package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Caches the pure route projection until identity, HQ, ownership, or connections change. */
public final class HqInspectionService {
    private static final HqInspection UNKNOWN = new HqInspection("Unknown", "", OptionalInt.empty());
    private static final HqInspection NO_HEADQUARTERS =
            new HqInspection("No Hq!", "", OptionalInt.empty(), 0xFFFF5555);
    private final TerritorySnapshotSource source;
    private final TerritoryRouteCalculator routes;
    private final QueueDurationEstimator durations;
    private TerritorySnapshot cachedSnapshot;
    private final Map<String, HqInspection> cache = new HashMap<>();

    public HqInspectionService(
            TerritorySnapshotSource source,
            TerritoryRouteCalculator routes,
            QueueDurationEstimator durations
    ) {
        this.source = source;
        this.routes = routes;
        this.durations = durations;
    }

    public HqInspection inspect(String territoryName) {
        var snapshot = source.snapshot();
        if (!snapshot.equals(cachedSnapshot)) {
            cachedSnapshot = snapshot;
            cache.clear();
            var projectedRoutes = routes.routes(snapshot);
            int ownedTerritories = (int) snapshot.territories().values().stream()
                    .filter(territory -> territory.reliable() && snapshot.guildName().equals(territory.owner()))
                    .count();
            int redThreshold = redThreshold(ownedTerritories);
            if (hasDefinitivelyNoHeadquarters(snapshot)) {
                projectedRoutes.keySet().forEach(name -> cache.put(name, NO_HEADQUARTERS));
            } else {
                projectedRoutes.forEach((name, route) -> cache.put(name, format(route, redThreshold)));
            }
        }
        return cache.getOrDefault(territoryName, UNKNOWN);
    }

    public boolean hasActiveAttackTimer(String territoryName) {
        return source.hasActiveAttackTimer(territoryName);
    }

    public void clear() {
        cachedSnapshot = null;
        cache.clear();
    }

    private HqInspection format(TerritoryRouteCalculator.Route route, int redThreshold) {
        return switch (route.status()) {
            case HEADQUARTERS -> new HqInspection("0", "", OptionalInt.of(0), distanceColor(0, redThreshold));
            case CONNECTED -> new HqInspection(
                    Integer.toString(route.connectionCount()),
                    formatDuration(durations.estimateSeconds(route.connectionCount())),
                    OptionalInt.of(route.connectionCount()), distanceColor(route.connectionCount(), redThreshold));
            case UNKNOWN -> UNKNOWN;
        };
    }

    /** A complete ownership projection with no holdings is a wipe, not an unknown route. */
    private static boolean hasDefinitivelyNoHeadquarters(TerritorySnapshot snapshot) {
        return snapshot.complete()
                && !snapshot.ownershipConflict()
                && !snapshot.guildName().isBlank()
                && !snapshot.territories().isEmpty()
                && snapshot.territories().values().stream().allMatch(territory -> !territory.owner().isBlank())
                && snapshot.territories().values().stream()
                        .noneMatch(territory -> snapshot.guildName().equals(territory.owner()));
    }

    static String formatDuration(int seconds) {
        return "\uD83D\uDD52 " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /** Guild-size heuristic; includes disconnected holdings, but never foreign or uncertain ownership. */
    static int redThreshold(int ownedTerritories) {
        return Math.max(1, (int) Math.round(1.25 * Math.sqrt(Math.max(0, ownedTerritories))));
    }

    /** Green at HQ, yellow at 60% of the threshold, red at and beyond the threshold. */
    static int distanceColor(int distance, int redThreshold) {
        double fraction = Math.clamp((double) distance / Math.max(1, redThreshold), 0, 1);
        int red = (int) Math.round(85 + 170 * Math.min(1, fraction / 0.6));
        int green = (int) Math.round(255 - 170 * Math.max(0, (fraction - 0.6) / 0.4));
        return 0xFF000055 | red << 16 | green << 8;
    }
}
