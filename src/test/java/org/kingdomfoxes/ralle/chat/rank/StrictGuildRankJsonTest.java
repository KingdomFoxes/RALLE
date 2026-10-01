package org.kingdomfoxes.ralle.chat.rank;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StrictGuildRankJsonTest {
    @Test
    void readsCurrentFoxTitlesAndPreservesPrimeMinisterPrecedence() {
        assertEquals(Map.of("Ana", "BARONESS", "Bo", "PRIME MINISTER"),
                StrictGuildRankJson.decodeApi("""
                        {"members":[
                          {"name":"Ana","fox_rank":"Baroness","prime_minister":false},
                          {"name":"Bo","fox_rank":"Lord","prime_minister":true},
                          {"name":"NoTitle","fox_rank":null},
                          {"name":"Malformed","fox_rank":42},
                          {"name":"Removed","fox_rank":null,"ranks":[{"kind":"fox","name":"Sir"}]}
                        ]}
                        """));
    }

    @Test
    void defaultRankEndpointFollowsTheProductionBackend() {
        assertEquals("https://kingdomfoxes.com/api/ranks", HttpGuildRankGateway.defaultEndpoint());
    }

    @Test
    void extractsOnlyFoxTitlesFromThePublicRankResponse() {
        String response = """
                {
                  "total_members": 3,
                  "members": [
                    {"name":"maxkarson","ranks":[
                      {"kind":"ingame","name":"strategist"},
                      {"kind":"fox","name":"Sir","section":"Court"}
                    ]},
                    {"name":"Vlou_Gremlin","ranks":[
                      {"kind":"ingame","name":"strategist"},
                      {"kind":"fox","name":"Knight"}
                    ]},
                    {"name":"NoFoxRank","ranks":[{"kind":"ingame","name":"captain"}]}
                  ]
                }
                """;

        var titles = StrictGuildRankJson.decodeApi(response);

        assertEquals(Map.of("maxkarson", "SIR", "Vlou_Gremlin", "KNIGHT"), titles);
        assertFalse(titles.containsKey("NoFoxRank"));
    }

    @Test
    void primeMinisterRoleOverridesBaseFoxRankAndCachesThePmPill() {
        var titles = StrictGuildRankJson.decodeApi("""
                {"members":[{"name":"ToolyTom","ingame_rank":"chief",
                  "fox_rank":"Lord","fox_section":"Prime Minister","prime_minister":true,
                  "ranks":[{"kind":"ingame","name":"chief"},
                           {"kind":"fox","name":"Lord","section":"Prime Minister"}]}]}
                """);
        assertEquals(Map.of("ToolyTom", "PRIME MINISTER"), titles);
        var snapshot = new GuildRankSnapshot(1234L, titles);
        var restored = StrictGuildRankJson.decodeCache(StrictGuildRankJson.encodeCache(snapshot));
        assertEquals("PRIME MINISTER", restored.titleFor("ToolyTom").orElseThrow());
        assertEquals(GuildRankTitleTransformer.encode("PM"), restored.glyphsFor("PRIME MINISTER"));
    }

    @Test
    void onlyAnExplicitBooleanTrueOverridesTheBaseRank() {
        for (String value : new String[]{"false", "null", "1", "\"true\"", "{}", "[]"}) {
            var titles = StrictGuildRankJson.decodeApi("""
                    {"members":[{"name":"ToolyTom","prime_minister":%s,
                     "ranks":[{"kind":"fox","name":"Lord"}]}]}
                    """.formatted(value));
            assertEquals(Map.of("ToolyTom", "LORD"), titles);
        }
    }

    @Test
    void cacheRoundTripPreservesTimestampAndNormalizedMappings() {
        var original = new GuildRankSnapshot(1234L, Map.of("MaxKarson", "Sir"));

        var restored = StrictGuildRankJson.decodeCache(StrictGuildRankJson.encodeCache(original));

        assertEquals(original, restored);
        assertEquals("SIR", restored.titleFor("MAXKARSON").orElseThrow());
    }
}
