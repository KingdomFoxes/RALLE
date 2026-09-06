package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HqInspectionServiceTest {
    @Test
    void formatsConnectionsAndProvisionalDuration() {
        var source = new MutableSource(snapshot("target", "Fox"));
        var service = new HqInspectionService(source, new TerritoryRouteCalculator(), new ProvisionalQueueDurationEstimator());

        var inspection = service.inspect("target");
        assertEquals("1", inspection.upperLabel());
        assertEquals("\uD83D\uDD52 2:00", inspection.lowerLabel());
        assertEquals(1, inspection.connectionCount().orElseThrow());
    }

    @Test
    void invalidatesCachedRoutesWhenSnapshotChanges() {
        var source = new MutableSource(routeSnapshot("Fox", "hq", "Fox", true));
        var service = new HqInspectionService(source, new TerritoryRouteCalculator(), new ProvisionalQueueDurationEstimator());
        assertEquals("2", service.inspect("target").upperLabel());

        source.snapshot = routeSnapshot("Fox", "hq", "Other", true);
        assertEquals("2", service.inspect("target").upperLabel());

        source.snapshot = routeSnapshot("Other", "hq", "Fox", true);
        assertEquals("Unknown", service.inspect("target").upperLabel());

        source.snapshot = routeSnapshot("Fox", "middle", "Fox", true);
        assertEquals("1", service.inspect("target").upperLabel());

        source.snapshot = routeSnapshot("Fox", "hq", "Fox", false);
        assertEquals("Unknown", service.inspect("target").upperLabel());
    }

    @Test
    void interpolatesDistanceColorAcrossTheWholeMapAndUpdatesWhenHqMoves() {
        var source = new MutableSource(routeSnapshot("Fox", "hq", "Other", true));
        var service = new HqInspectionService(source, new TerritoryRouteCalculator(), new ProvisionalQueueDurationEstimator());
        assertEquals(0xFF55FF55, service.inspect("hq").upperColor());
        assertEquals(0xFFAAAA55, service.inspect("middle").upperColor());
        assertEquals(0xFFFF5555, service.inspect("target").upperColor());
        assertEquals("\uD83D\uDD52 3:00", service.inspect("target").lowerLabel());

        source.snapshot = routeSnapshot("Fox", "middle", "Fox", true);
        assertEquals(0xFF55FF55, service.inspect("middle").upperColor());
        assertEquals(0xFFFF5555, service.inspect("hq").upperColor());
        assertEquals(0xFF55FF55, HqInspectionService.distanceColor(0, 0));
    }

    @Test
    void explicitClearDropsTheCachedProjection() {
        var source = new MutableSource(snapshot("target", "Fox"));
        var estimates = new AtomicInteger();
        QueueDurationEstimator estimator = count -> {
            estimates.incrementAndGet();
            return 120;
        };
        var service = new HqInspectionService(source, new TerritoryRouteCalculator(), estimator);

        service.inspect("target");
        service.inspect("target");
        assertEquals(1, estimates.get());
        service.clear();
        service.inspect("target");
        assertEquals(2, estimates.get());
    }

    @Test
    void unknownAndHeadquartersNeverHaveDurations() {
        var source = new MutableSource(new TerritorySnapshot("", Map.of(), false, false));
        var service = new HqInspectionService(source, new TerritoryRouteCalculator(), new ProvisionalQueueDurationEstimator());
        assertEquals("Unknown", service.inspect("missing").upperLabel());
        assertFalse(service.inspect("missing").hasLowerLabel());

        source.snapshot = new TerritorySnapshot("Fox", Map.of("hq", node("hq", "Fox", true)), true, false);
        assertEquals("0", service.inspect("hq").upperLabel());
        assertFalse(service.inspect("hq").hasLowerLabel());
    }

    private static TerritorySnapshot snapshot(String target, String owner) {
        return new TerritorySnapshot("Fox", Map.of(
                "hq", node("hq", "Fox", true, target),
                target, node(target, owner, false, "hq")), true, false);
    }

    private static TerritorySnapshot routeSnapshot(
            String guildName, String headquarters, String middleOwner, boolean connected
    ) {
        return new TerritorySnapshot(guildName, Map.of(
                "hq", node("hq", "Fox", "hq".equals(headquarters), "middle"),
                "middle", node("middle", middleOwner, "middle".equals(headquarters), connected ? "target" : "hq"),
                "target", node("target", "Other", false, connected ? "middle" : "target")), true, false);
    }

    private static TerritorySnapshot.Territory node(
            String name, String owner, boolean headquarters, String... connections
    ) {
        return new TerritorySnapshot.Territory(name, owner, headquarters, Set.of(connections));
    }

    private static final class MutableSource implements TerritorySnapshotSource {
        private TerritorySnapshot snapshot;
        private MutableSource(TerritorySnapshot snapshot) { this.snapshot = snapshot; }
        @Override public TerritorySnapshot snapshot() { return snapshot; }
        @Override public boolean hasActiveAttackTimer(String territoryName) { return false; }
    }
}
