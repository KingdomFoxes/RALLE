package org.kingdomfoxes.ralle.requeue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidReadyTrackerTest {
    @TempDir Path directory;

    @Test
    void anotherPlayersQueueReplacesPersistedTccWithNotg() {
        var path = directory.resolve("requeue.properties");
        var store = new AutoRaidRequeueStore(path);
        var tracker = new RaidReadyTracker();
        tracker.observe("LocalPlayer would like to start The Canyon Colossus! Click here to Ready Up!", 0)
                .ifPresent(store::remember);
        var reloaded = new AutoRaidRequeueStore(path);
        assertEquals(WynnRaid.TCC, reloaded.lastRaid().orElseThrow());
        assertTrue(tracker.observe("OtherPlayer would like to start Nest of the Grootslangs!", 100).isEmpty());
        tracker.observe("Click here to Ready Up!", 101).ifPresent(reloaded::remember);
        assertEquals(WynnRaid.NOTG, new AutoRaidRequeueStore(path).lastRaid().orElseThrow());
    }

    @Test
    void acceptsCombinedPromptsFromAnyInitiatorForEveryRaid() {
        for (var speaker : new String[]{"LocalPlayer", "OtherPlayer"}) {
            for (var raid : WynnRaid.values()) {
                assertEquals(raid, new RaidReadyTracker().observe(speaker + " would like to start "
                        + raid.displayName() + "! Click here to Ready Up!", 0).orElseThrow());
            }
        }
    }

    @Test
    void newerAnnouncementReplacesPendingRaidAndReadyIsConsumedOnce() {
        var tracker = new RaidReadyTracker();
        tracker.observe("LocalPlayer would like to start The Canyon Colossus!", 0);
        tracker.observe("OtherPlayer would like to start Nest of the Grootslangs!", 1);
        assertEquals(WynnRaid.NOTG, tracker.observe("Click here to Ready Up!", 2).orElseThrow());
        assertTrue(tracker.observe("Click here to Ready Up!", 3).isEmpty());
    }

    @Test
    void expiredClearedAndClockResetAnnouncementsCannotUpdateRaid() {
        for (int readyTick : new int[]{99, 141}) {
            var tracker = new RaidReadyTracker();
            tracker.observe("OtherPlayer would like to start Nest of the Grootslangs!", 100);
            assertTrue(tracker.observe("Click here to Ready Up!", readyTick).isEmpty());
        }
        var tracker = new RaidReadyTracker();
        tracker.observe("OtherPlayer would like to start Nest of the Grootslangs!", 100);
        tracker.clear();
        assertTrue(tracker.observe("Click here to Ready Up!", 101).isEmpty());
    }

    @Test
    void completionUnknownRaidsAndUnpairedReadyDoNotUpdateRaid() {
        var tracker = new RaidReadyTracker();
        assertTrue(tracker.observe("OtherPlayer completed Nest of the Grootslangs!", 0).isEmpty());
        assertTrue(tracker.observe("OtherPlayer would like to start The Forgery! Click here to Ready Up!", 1).isEmpty());
        assertTrue(tracker.observe("Click here to Ready Up!", 2).isEmpty());
    }
}
