package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shortest link distances from the player's HQ across all guilds' territories. */
public final class TerritoryRouteCalculator {
    public Route route(TerritorySnapshot snapshot, String destinationName) {
        return routes(snapshot).getOrDefault(destinationName, Route.unknown());
    }

    public Map<String, Route> routes(TerritorySnapshot snapshot) {
        var result = new HashMap<String, Route>();
        snapshot.territories().keySet().forEach(name -> result.put(name, Route.unknown()));
        if (!snapshot.complete() || snapshot.ownershipConflict() || snapshot.guildName().isBlank()) {
            return result;
        }

        var headquarters = snapshot.territories().values().stream()
                .filter(territory -> territory.headquarters() && ownedBy(snapshot, territory))
                .toList();
        if (headquarters.size() != 1 || !headquarters.getFirst().reliable()) return result;
        result.put(headquarters.getFirst().name(), Route.headquarters());

        var graph = normalizedGraph(snapshot.territories());
        var distances = new HashMap<String, Integer>();
        var queue = new ArrayDeque<String>();
        distances.put(headquarters.getFirst().name(), 0);
        queue.add(headquarters.getFirst().name());

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (var neighbor : graph.getOrDefault(current, Set.of())) {
                if (distances.containsKey(neighbor)) continue;
                var territory = snapshot.territories().get(neighbor);
                // Ownership can lag behind captures; it does not change physical links.
                if (territory == null) continue;
                int distance = distances.get(current) + 1;
                result.put(neighbor, Route.connected(distance));
                distances.put(neighbor, distance);
                queue.addLast(neighbor);
            }
        }
        return result;
    }

    private static boolean ownedBy(TerritorySnapshot snapshot, TerritorySnapshot.Territory territory) {
        return snapshot.guildName().equals(territory.owner());
    }

    private static Map<String, Set<String>> normalizedGraph(Map<String, TerritorySnapshot.Territory> territories) {
        var graph = new LinkedHashMap<String, Set<String>>();
        territories.keySet().forEach(name -> graph.put(name, new HashSet<>()));
        for (var territory : territories.values()) {
            for (var connected : territory.connections()) {
                if (!territories.containsKey(connected)) continue;
                graph.get(territory.name()).add(connected);
                graph.get(connected).add(territory.name());
            }
        }
        return graph;
    }

    public record Route(Status status, int connectionCount) {
        public enum Status { CONNECTED, HEADQUARTERS, UNKNOWN }

        public static Route connected(int count) { return new Route(Status.CONNECTED, count); }
        public static Route headquarters() { return new Route(Status.HEADQUARTERS, 0); }
        public static Route unknown() { return new Route(Status.UNKNOWN, -1); }
    }
}
