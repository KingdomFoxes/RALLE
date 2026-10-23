package org.kingdomfoxes.ralle.chat.rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import org.kingdomfoxes.ralle.settings.LocalStateSection;

/** Reads and atomically replaces the instance-local guild-rank snapshot. */
final class GuildRankCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRankCache.class);
    private final LocalStateSection path;

    GuildRankCache(Path path) {
        this(LocalStateSection.file(path));
    }

    GuildRankCache(LocalStateSection path) {
        this.path = path;
    }

    GuildRankSnapshot load() {
        if (!path.exists()) return GuildRankSnapshot.EMPTY;
        try {
            return StrictGuildRankJson.decodeCache(path.read());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read RALLE guild-rank cache from {}", path, exception);
            return GuildRankSnapshot.EMPTY;
        }
    }

    void save(GuildRankSnapshot snapshot) throws IOException {
        path.write(StrictGuildRankJson.encodeCache(snapshot));
    }
}
