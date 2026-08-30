package org.kingdomfoxes.ralle.requeue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRaidRequeueStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsOnlyTheLastRaidAcrossInstances() throws Exception {
        Path path = temporaryDirectory.resolve("ralle-auto-requeue.properties");
        var store = new AutoRaidRequeueStore(path);

        assertTrue(store.lastRaid().isEmpty());
        store.remember(WynnRaid.TWP);

        assertEquals(WynnRaid.TWP, new AutoRaidRequeueStore(path).lastRaid().orElseThrow());
        String persisted = Files.readString(path);
        assertTrue(persisted.contains("last-raid=the-warront-palace"));
        assertTrue(!persisted.contains("maxkarson"));
    }

    @Test
    void ignoresUnknownPersistedRaidValues() throws Exception {
        Path path = temporaryDirectory.resolve("ralle-auto-requeue.properties");
        Files.writeString(path, "last-raid=unknown\n");

        assertTrue(new AutoRaidRequeueStore(path).lastRaid().isEmpty());
    }
}
