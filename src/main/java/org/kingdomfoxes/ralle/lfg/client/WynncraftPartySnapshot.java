package org.kingdomfoxes.ralle.lfg.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Local discovery only. Fox resolves these names and owns all slot reservations. */
public record WynncraftPartySnapshot(String leader, List<String> members) {
    public WynncraftPartySnapshot {
        members = List.copyOf(members);
    }

    public Optional<List<String>> otherMembersForHost(String localIgn) {
        if (leader == null || !leader.equalsIgnoreCase(localIgn) || members.size() < 2
                || members.stream().noneMatch(name -> name.equalsIgnoreCase(localIgn))
                || members.stream().anyMatch(name -> !name.matches("[A-Za-z0-9_]{1,16}"))
                || members.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct().count() != members.size()) {
            return Optional.empty();
        }
        return Optional.of(members.stream().filter(name -> !name.equalsIgnoreCase(localIgn)).toList());
    }
}
