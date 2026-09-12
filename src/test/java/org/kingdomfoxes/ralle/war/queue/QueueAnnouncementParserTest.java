package org.kingdomfoxes.ralle.war.queue;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.identity.GuildChatMessage;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueAnnouncementParserTest {
    private final QueueAnnouncementParser parser = new QueueAnnouncementParser();

    @Test
    void acceptsEveryDefenseValueAndCanonicalTerritoryPunctuation() {
        var territories = List.of("Corkus' Castle", "The Forgery - Entrance");
        for (String defense : List.of("None", "Very Low", "Low", "Medium", "High", "Very High")) {
            assertEquals(new QueueAnnouncement("Corkus' Castle", "MaxKarson"), parser.parse(
                    message("Corkus' Castle defense is " + defense), territories).orElseThrow());
        }
        assertEquals("The Forgery - Entrance", parser.parse(
                message("  The   Forgery - Entrance defense is High  "), territories)
                .orElseThrow().territoryName());
    }

    @Test
    void rejectsNearMatchesUnknownTerritoriesSuffixesAndLineBreaks() {
        var territories = List.of("Detlas");
        assertTrue(parser.parse(message("Detlas defense is Highest"), territories).isEmpty());
        assertTrue(parser.parse(message("Detlas defense is High today"), territories).isEmpty());
        assertTrue(parser.parse(message("Detlas Suburb defense is High"), territories).isEmpty());
        assertTrue(parser.parse(message("detlas defense is High"), territories).isEmpty());
        assertTrue(parser.parse(message("Detlas defense is High\nquoted"), territories).isEmpty());
    }

    @Test
    void acceptsServerContinuationWithinTerritoryAndDefense() {
        String continuation = "\n\uDAFF\uDFFC\uE001\uDB00\uDC06 ";
        for (String body : List.of(
                "Hobgoblin's Hoard defense is" + continuation + "Very Low",
                "Hobgoblin's" + continuation + "Hoard defense is Very Low",
                "Hobgoblin's Hoard defense is Very" + continuation + "Low")) {
            assertEquals(new QueueAnnouncement("Hobgoblin's Hoard", "MaxKarson"),
                    parser.parse(message(body), List.of("Hobgoblin's Hoard")).orElseThrow());
        }
        for (String body : List.of(
                "Detlas defense is\nVery Low",
                "Detlas defense is\n\uE001 Very Low",
                "Detlas defense is High" + continuation + "quoted",
                "Detlas defense is High" + continuation + "Ragni defense is Low")) {
            assertTrue(parser.parse(message(body), List.of("Detlas", "Ragni")).isEmpty());
        }
    }

    @Test
    void unresolvedNicknameCannotBecomeAnAuthoritativeIgn() {
        var unresolved = new GuildChatMessage("A Class Name", Optional.empty(), "Detlas defense is High");
        assertTrue(parser.parse(unresolved, List.of("Detlas")).isEmpty());
        var invalid = new GuildChatMessage("Invalid", Optional.of("too-long-player-name"), "Detlas defense is High");
        assertTrue(parser.parse(invalid, List.of("Detlas")).isEmpty());
    }

    private static GuildChatMessage message(String body) {
        return new GuildChatMessage("MaxKarson", Optional.of("MaxKarson"), body);
    }
}
