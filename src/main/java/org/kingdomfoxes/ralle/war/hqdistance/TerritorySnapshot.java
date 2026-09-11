package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable projection of Wynntils territory, ownership, HQ, and connection data. */
public record TerritorySnapshot(
        String guildName,
        Map<String, Territory> territories,
        boolean complete,
        boolean ownershipConflict
) {
    public TerritorySnapshot {
        guildName = Objects.requireNonNullElse(guildName, "").strip();
        var copy = new LinkedHashMap<String, Territory>();
        Objects.requireNonNull(territories, "territories").forEach((name, territory) ->
                copy.put(Objects.requireNonNull(name, "territory name"), Objects.requireNonNull(territory, "territory")));
        territories = Map.copyOf(copy);
    }

    public record Territory(String name, String owner, boolean headquarters, Set<String> connections, boolean reliable) {
        public Territory(String name, String owner, boolean headquarters, Set<String> connections) {
            this(name, owner, headquarters, connections, true);
        }

        /** Reliability applies to ownership/HQ identification, never to physical link traversal. */
        public static Territory observed(
                String name, String owner, String profileOwner, boolean headquarters, Set<String> connections) {
            boolean reliable = owner != null && !owner.isBlank() && owner.equals(profileOwner)
                    && connections != null;
            return new Territory(name, owner, headquarters, connections == null ? Set.of() : connections, reliable);
        }

        public Territory {
            name = Objects.requireNonNull(name, "name");
            owner = Objects.requireNonNullElse(owner, "");
            connections = Set.copyOf(Objects.requireNonNull(connections, "connections"));
        }
    }
}
