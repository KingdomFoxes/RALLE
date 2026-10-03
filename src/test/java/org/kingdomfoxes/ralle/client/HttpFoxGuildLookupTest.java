package org.kingdomfoxes.ralle.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HttpFoxGuildLookupTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void recognizesOnlyFoxAndRequiresMatchingCanonicalIdentity() {
        assertTrue(HttpFoxGuildLookup.isFoxMember(profile("{\"prefix\":\"Fox\"}"), PLAYER));
        assertFalse(HttpFoxGuildLookup.isFoxMember(profile("{\"prefix\":\"ALLY\"}"), PLAYER));
        assertFalse(HttpFoxGuildLookup.isFoxMember(profile("null"), PLAYER));
        assertFalse(HttpFoxGuildLookup.isFoxMember(profile("{\"prefix\":\"FOX-extra\"}"), PLAYER));
        assertThrows(IllegalArgumentException.class,
                () -> HttpFoxGuildLookup.isFoxMember(profile("{\"prefix\":\"FOX\"}"), UUID.randomUUID()));
    }

    @Test
    void malformedHiddenOrIncompleteProfilesCannotUnlockSettings() {
        for (var json : new String[]{"{}", "[]", "not json", "{\"uuid\":\"bad\",\"guild\":null}",
                "{\"uuid\":\"" + PLAYER + "\"}", profile("{}"), profile("{\"prefix\":123}"),
                profile("\"FOX\"")}) {
            assertThrows(RuntimeException.class, () -> HttpFoxGuildLookup.isFoxMember(json, PLAYER), json);
        }
    }

    private static String profile(String guild) {
        return "{\"uuid\":\"" + PLAYER + "\",\"guild\":" + guild + "}";
    }
}
