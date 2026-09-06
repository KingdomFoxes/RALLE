package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerritoryRouteCalculatorTest {
    private final TerritoryRouteCalculator calculator = new TerritoryRouteCalculator();

    @Test
    void findsShortestOwnedPathAcrossCyclesAndNormalizesOneWayLinks() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "a"),
                node("a", "Fox", false, "b", "c"),
                node("b", "Fox", false, "hq", "c"),
                node("c", "Fox", false));

        assertEquals(new TerritoryRouteCalculator.Route(TerritoryRouteCalculator.Route.Status.CONNECTED, 2),
                calculator.route(snapshot, "c"));
    }

    @Test
    void traversesForeignTerritoriesToReachDistantDestinations() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "owned"),
                node("owned", "Fox", false, "enemy"),
                node("enemy", "Other", false, "far-enemy"),
                node("far-enemy", "Other", false));

        assertEquals(2, calculator.route(snapshot, "enemy").connectionCount());
        assertEquals(3, calculator.route(snapshot, "far-enemy").connectionCount());
    }

    @Test
    void ownedDestinationIsEstimatedHypotheticallyAndDisconnectedHoldingHasNoRoute() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "owned"),
                node("owned", "Fox", false),
                node("island", "Fox", false));

        assertEquals(1, calculator.route(snapshot, "owned").connectionCount());
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN,
                calculator.route(snapshot, "island").status());
    }

    @Test
    void missingEndpointsAreIgnoredAndIncompleteOrConflictingDataIsUnknown() {
        var valid = snapshot(node("hq", "Fox", true, "missing"), node("target", "Other", false));
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN,
                calculator.route(valid, "target").status());

        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN,
                calculator.route(new TerritorySnapshot("Fox", valid.territories(), false, false), "target").status());
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN,
                calculator.route(new TerritorySnapshot("Fox", valid.territories(), true, true), "target").status());
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN,
                calculator.route(new TerritorySnapshot("Fox", Map.of("target", node("target", "Fox", false)), true, false), "target").status());
    }

    @Test
    void unrelatedStaleOwnershipOrIncompleteDataDoesNotInvalidateAnOwnedRoute() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "target", "stale"),
                node("target", "Fox", false),
                TerritorySnapshot.Territory.observed("stale", "Other", "Captured", false, Set.of()),
                TerritorySnapshot.Territory.observed("incomplete", null, "Other", false, null));

        assertEquals(1, calculator.route(snapshot, "target").connectionCount());
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN, calculator.route(snapshot, "stale").status());
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN, calculator.route(snapshot, "incomplete").status());
    }

    @Test
    void uncertainIntermediateAndHeadquartersCannotAuthorizeNumericDistances() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "middle"),
                TerritorySnapshot.Territory.observed("middle", "Fox", "Other", false, Set.of("target")),
                node("target", "Fox", false));
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN, calculator.route(snapshot, "target").status());

        var staleHq = snapshot(
                TerritorySnapshot.Territory.observed("hq", "Fox", "Other", true, Set.of("target")),
                node("target", "Fox", false));
        assertEquals(TerritoryRouteCalculator.Route.Status.UNKNOWN, calculator.route(staleHq, "target").status());
    }

    @Test
    void routeRecoversWhenOwnershipSourcesAgreeAgain() {
        var snapshot = snapshot(
                node("hq", "Fox", true, "middle"),
                TerritorySnapshot.Territory.observed("middle", "Fox", "Fox", false, Set.of("target")),
                node("target", "Fox", false));
        assertEquals(2, calculator.route(snapshot, "target").connectionCount());
    }

    @Test
    void headquartersIsZeroWithoutTraversing() {
        assertEquals(new TerritoryRouteCalculator.Route(TerritoryRouteCalculator.Route.Status.HEADQUARTERS, 0),
                calculator.route(snapshot(node("hq", "Fox", true)), "hq"));
    }

    private static TerritorySnapshot snapshot(TerritorySnapshot.Territory... nodes) {
        var map = new LinkedHashMap<String, TerritorySnapshot.Territory>();
        for (var node : nodes) map.put(node.name(), node);
        return new TerritorySnapshot("Fox", map, true, false);
    }

    private static TerritorySnapshot.Territory node(
            String name, String owner, boolean headquarters, String... connections
    ) {
        return new TerritorySnapshot.Territory(name, owner, headquarters, Set.of(connections));
    }
}
