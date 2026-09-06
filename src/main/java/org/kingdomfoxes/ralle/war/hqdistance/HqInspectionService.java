package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Caches the pure route projection until identity, HQ, ownership, or connections change. */
public final class HqInspectionService {
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
            int furthest = projectedRoutes.values().stream()
                    .mapToInt(TerritoryRouteCalculator.Route::connectionCount).max().orElse(0);
            projectedRoutes.forEach((name, route) -> cache.put(name, format(route, furthest)));
        }
        return cache.getOrDefault(territoryName, format(TerritoryRouteCalculator.Route.unknown(), 0));
    }

    public boolean hasActiveAttackTimer(String territoryName) {
        return source.hasActiveAttackTimer(territoryName);
    }

    public void clear() {
        cachedSnapshot = null;
        cache.clear();
    }

    private HqInspection format(TerritoryRouteCalculator.Route route, int furthest) {
        return switch (route.status()) {
            case HEADQUARTERS -> new HqInspection("0", "", OptionalInt.of(0), distanceColor(0, furthest));
            case CONNECTED -> new HqInspection(
                    Integer.toString(route.connectionCount()),
                    formatDuration(durations.estimateSeconds(route.connectionCount())),
                    OptionalInt.of(route.connectionCount()), distanceColor(route.connectionCount(), furthest));
            case UNKNOWN -> new HqInspection("Unknown", "", OptionalInt.empty());
        };
    }

    static String formatDuration(int seconds) {
        return "\uD83D\uDD52 " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /** Linear RGB interpolation from HQ green to the furthest reachable territory's red. */
    static int distanceColor(int distance, int furthest) {
        float fraction = furthest <= 0 ? 0 : Math.clamp((float) distance / furthest, 0, 1);
        int red = Math.round(85 + 170 * fraction);
        int green = Math.round(255 - 170 * fraction);
        return 0xFF000055 | red << 16 | green << 8;
    }
}
