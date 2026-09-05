package org.kingdomfoxes.ralle.requeue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidStartMessageTest {
    @Test
    void parsesTheTwoLineWynncraftReadyPrompt() {
        var parsed = RaidStartMessage.parse("\uE000 maxkarson would like to start The Nameless Anomaly!\n"
                + "Click here to Ready Up!").orElseThrow();

        assertEquals("\uE000 maxkarson", parsed.speaker());
        assertEquals(WynnRaid.TNA, parsed.raid());
    }

    @Test
    void parsesTheAnnouncementWhenWynncraftSendsReadyAsASeparateMessage() {
        var parsed = RaidStartMessage.parseAnnouncement(
                "󏿼󏿿󏿾 maxkarson would like to start Nest of the Grootslangs!").orElseThrow();

        assertEquals("󏿼󏿿󏿾 maxkarson", parsed.speaker());
        assertEquals(WynnRaid.NOTG, parsed.raid());
        assertTrue(RaidStartMessage.isReadyPrompt("󏿼󐀆 󐀸Click here to Ready Up!"));
    }

    @Test
    void acceptsEverySupportedRaidName() {
        for (var raid : WynnRaid.values()) {
            var parsed = RaidStartMessage.parse("Cinq would like to start " + raid.displayName()
                    + "! Click here to Ready Up!").orElseThrow();
            assertEquals(raid, parsed.raid());
        }
    }

    @Test
    void rejectsSimilarMessagesAndUnknownDestinations() {
        assertTrue(RaidStartMessage.parse("maxkarson completed The Nameless Anomaly!").isEmpty());
        assertTrue(RaidStartMessage.parse("maxkarson would like to start The Forgery! Click here to Ready Up!").isEmpty());
        assertTrue(RaidStartMessage.parse("maxkarson would like to start The Nameless Anomaly!").isEmpty());
    }

    @Test
    void partyQueueCoordinatesResolveToTheReportedContainerSlot() {
        assertEquals(49, AutoRaidRequeueController.inventorySlot(6, 5));
    }
}
