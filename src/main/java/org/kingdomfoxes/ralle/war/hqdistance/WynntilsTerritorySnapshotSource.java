package org.kingdomfoxes.ralle.war.hqdistance;

import com.wynntils.core.components.Models;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Read-only adapter over Wynntils 4.2.7's already-maintained guild and territory models. */
final class WynntilsTerritorySnapshotSource implements TerritorySnapshotSource {
    @Override
    public TerritorySnapshot snapshot() {
        String guildName = Models.Guild.getGuildName();
        var territories = new LinkedHashMap<String, TerritorySnapshot.Territory>();
        boolean complete = guildName != null && !guildName.isBlank();

        for (var poi : Models.Territory.getTerritoryPoisFromAdvancement()) {
            var info = poi.getTerritoryInfo();
            var profile = poi.getTerritoryProfile();
            if (profile == null) continue;
            var name = profile.getName();
            territories.put(name, TerritorySnapshot.Territory.observed(
                    name,
                    info == null ? null : info.getGuildName(),
                    profile.getGuild(),
                    info != null && info.isHeadquarters(),
                    info == null || info.getTradingRoutes() == null ? null : info.getTradingRoutes().stream()
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
        }
        if (territories.isEmpty()) complete = false;
        return new TerritorySnapshot(guildName, territories, complete, false);
    }

    @Override
    public boolean hasActiveAttackTimer(String territoryName) {
        return Models.GuildAttackTimer.getAttackTimerForTerritory(territoryName).isPresent();
    }
}
