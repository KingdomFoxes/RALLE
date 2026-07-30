package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Detects Wynncraft's current EU/NA/AS region from locally synchronized server labels. */
public final class MinecraftRaidRegionDetector implements RaidRegionDetector {
    private static final Pattern SERVER_NAME =
            Pattern.compile("(?i)(?:^|[^A-Z0-9])(EU|NA|AS)[-_ ]?\\d{1,3}(?:$|[^A-Z0-9])");

    private final Minecraft minecraft;

    public MinecraftRaidRegionDetector(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public Optional<LfgProtocol.Region> detect() {
        var candidates = new ArrayList<String>();
        var server = minecraft.getCurrentServer();
        collect(candidates, server == null ? null : server.name);
        collect(candidates, server == null ? null : server.ip);
        collect(candidates, server == null ? null : server.status);
        collect(candidates, server == null ? null : server.motd);
        if (server != null && server.playerList != null) {
            server.playerList.forEach(component -> collect(candidates, component));
        }

        var connection = minecraft.getConnection();
        if (connection != null) {
            var connectedServer = connection.getServerData();
            collect(candidates, connectedServer == null ? null : connectedServer.name);
            collect(candidates, connectedServer == null ? null : connectedServer.ip);
            for (var info : connection.getOnlinePlayers()) {
                collect(candidates, info.getTabListDisplayName());
            }
            var scoreboard = connection.scoreboard();
            for (var objective : scoreboard.getObjectives()) {
                collect(candidates, objective.getName());
                collect(candidates, objective.getDisplayName());
                for (var score : scoreboard.listPlayerScores(objective)) {
                    collect(candidates, score.owner());
                    collect(candidates, score.display());
                }
            }
            for (var team : scoreboard.getPlayerTeams()) {
                collect(candidates, team.getName());
                collect(candidates, team.getDisplayName());
                collect(candidates, team.getPlayerPrefix());
                collect(candidates, team.getPlayerSuffix());
            }
        }
        return detect(candidates);
    }

    static Optional<LfgProtocol.Region> detect(Iterable<String> candidates) {
        for (var candidate : candidates) {
            if (candidate == null) continue;
            var matcher = SERVER_NAME.matcher(candidate);
            if (matcher.find()) {
                return Optional.of(LfgProtocol.Region.valueOf(matcher.group(1).toUpperCase(Locale.ROOT)));
            }
        }
        return Optional.empty();
    }

    private static void collect(ArrayList<String> candidates, String value) {
        if (value != null && !value.isBlank()) candidates.add(value);
    }

    private static void collect(ArrayList<String> candidates, Component value) {
        if (value != null) collect(candidates, value.getString());
    }
}
