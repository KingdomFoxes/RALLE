package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.StrictLfgJson;
import com.google.gson.JsonParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WynncraftPartySnapshotTest {
    @Test void onlyKnownHostWithOtherMembersGetsAnOffer() {
        assertTrue(new WynncraftPartySnapshot(null, List.of()).otherMembersForHost("Host").isEmpty());
        assertTrue(new WynncraftPartySnapshot("Other", List.of("Host", "Other")).otherMembersForHost("Host").isEmpty());
        assertTrue(new WynncraftPartySnapshot("Host", List.of("Host")).otherMembersForHost("Host").isEmpty());
        assertTrue(new WynncraftPartySnapshot("Host", List.of("Other", "Third")).otherMembersForHost("Host").isEmpty());
        assertEquals(List.of("First", "Second"), new WynncraftPartySnapshot("Host", List.of("First", "HOST", "Second"))
                .otherMembersForHost("host").orElseThrow());
    }

    @Test void oversizedPartyIsKeptWholeForThePopupToExplainCapacity() {
        assertEquals(4, new WynncraftPartySnapshot("Host", List.of("Host", "One", "Two", "Three", "Four"))
                .otherMembersForHost("Host").orElseThrow().size());
    }

    @Test void wireRequestOmitsEmptyImportsAndRejectsInvalidOrDuplicateNames() {
        var solo = JsonParser.parseString(StrictLfgJson.createRequest(LfgProtocol.RaidType.TNA,
                LfgProtocol.Region.EU, null)).getAsJsonObject();
        assertFalse(solo.has("party_members"));
        var party = JsonParser.parseString(StrictLfgJson.createRequest(LfgProtocol.RaidType.TNA,
                LfgProtocol.Region.EU, null, List.of("Player01", "Player02"))).getAsJsonObject();
        assertEquals(2, party.getAsJsonArray("party_members").size());
        for (var invalid : List.of(List.of("Player", "player"), List.of("/pa disband"),
                List.of("One", "Two", "Three", "Four"))) {
            assertThrows(IllegalArgumentException.class, () -> StrictLfgJson.createRequest(
                    LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null, invalid));
        }
    }
}
