package org.kingdomfoxes.ralle.requeue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import org.kingdomfoxes.ralle.settings.LocalStateSection;
import java.util.Optional;
import java.util.Properties;

/** Stores only the last locally observed raid type; no identity or raid history is retained. */
public final class AutoRaidRequeueStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoRaidRequeueStore.class);
    private static final String LAST_RAID = "last-raid";

    private final LocalStateSection path;
    private WynnRaid lastRaid;

    public AutoRaidRequeueStore(Path path) {
        this(LocalStateSection.file(path));
    }

    public AutoRaidRequeueStore(LocalStateSection path) {
        this.path = path;
        load();
    }

    public Optional<WynnRaid> lastRaid() {
        return Optional.ofNullable(lastRaid);
    }

    public void remember(WynnRaid raid) {
        if (raid == lastRaid) return;
        lastRaid = raid;
        var properties = new Properties();
        properties.setProperty(LAST_RAID, raid.persistedId());
        try {
            path.writeProperties(properties);
        } catch (IOException exception) {
            LOGGER.error("Could not save automatic raid requeue state to {}", path, exception);
        }
    }

    private void load() {
        if (!path.exists()) return;
        try {
            var properties = path.readProperties();
            lastRaid = WynnRaid.fromPersistedId(properties.getProperty(LAST_RAID)).orElse(null);
        } catch (IOException exception) {
            LOGGER.warn("Could not read automatic raid requeue state from {}", path, exception);
        }
    }
}
