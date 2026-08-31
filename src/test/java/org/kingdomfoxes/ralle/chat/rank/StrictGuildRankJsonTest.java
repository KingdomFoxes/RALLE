package org.kingdomfoxes.ralle.chat.rank;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StrictGuildRankJsonTest {
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
    void cacheRoundTripPreservesTimestampAndNormalizedMappings() {
        var original = new GuildRankSnapshot(1234L, Map.of("MaxKarson", "Sir"));

        var restored = StrictGuildRankJson.decodeCache(StrictGuildRankJson.encodeCache(original));

        assertEquals(original, restored);
        assertEquals("SIR", restored.titleFor("MAXKARSON").orElseThrow());
    }
}
