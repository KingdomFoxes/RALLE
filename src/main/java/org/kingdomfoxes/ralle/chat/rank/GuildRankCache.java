package org.kingdomfoxes.ralle.chat.rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and atomically replaces the instance-local guild-rank snapshot. */
final class GuildRankCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRankCache.class);
    private final Path path;

    GuildRankCache(Path path) {
        this.path = path;
    }

    GuildRankSnapshot load() {
        if (!Files.exists(path)) return GuildRankSnapshot.EMPTY;
        try {
            return StrictGuildRankJson.decodeCache(Files.readString(path));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read RALLE guild-rank cache from {}", path, exception);
            return GuildRankSnapshot.EMPTY;
        }
    }

    void save(GuildRankSnapshot snapshot) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, StrictGuildRankJson.encodeCache(snapshot));
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
