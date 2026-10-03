package org.kingdomfoxes.ralle.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthUpdateLookupTest {
    private static String listing(String id, String number, String name, String loader, String game, String status) {
        return """
                {"id":"%s","version_number":"%s","name":"%s",
                 "loaders":["%s"],"game_versions":["%s"],"status":"%s","version_type":"release"}
                """.formatted(id, number, name, loader, game, status);
    }

    private static String listing(String number) {
        return listing("ABCDEFGH", number, "RALLE " + number, "fabric", "1.21.11", "listed");
    }

    @Test
    void selectsHighestCompatibleVersionRegardlessOfResponseOrder() {
        var json = "[" + listing("0.1.9") + "," + listing("0.1.10") + "," + listing("0.1.8") + "]";
        var release = ModrinthUpdateLookup.newerRelease(json, "0.1.8", "1.21.11").orElseThrow();
        assertEquals("0.1.10", release.versionNumber());
        assertEquals("RALLE 0.1.10", release.name());
        assertEquals("https://modrinth.com/project/ralle/version/ABCDEFGH", release.changelogUrl().toString());
    }

    @Test
    void neverRecommendsOlderEqualOrBuildMetadataOnlyVersions() {
        for (var number : new String[]{"0.1.7", "0.1.8", "0.1.8+build.2", "0.1.8-beta.1"}) {
            assertTrue(ModrinthUpdateLookup.newerRelease("[" + listing(number) + "]", "0.1.8", "1.21.11").isEmpty());
        }
        assertTrue(ModrinthUpdateLookup.newerRelease("[]", "0.1.8", "1.21.11").isEmpty());
        assertTrue(ModrinthUpdateLookup.newerRelease("[" + listing("0.1.9") + "]", "development", "1.21.11").isEmpty());
    }

    @Test
    void supportsNewerPrereleasesAndUpgradingPrereleaseToFinal() {
        assertEquals("0.1.9-beta.2", ModrinthUpdateLookup.newerRelease(
                "[" + listing("0.1.9-beta.2").replace("\"release\"", "\"beta\"") + "]",
                "0.1.9-beta.1", "1.21.11").orElseThrow().versionNumber());
        assertEquals("0.1.9", ModrinthUpdateLookup.newerRelease(
                "[" + listing("0.1.9") + "]", "0.1.9-beta.2", "1.21.11").orElseThrow().versionNumber());
    }

    @Test
    void ignoresIncompatibleUnpublishedAndMalformedListingsWithoutHidingValidUpdate() {
        var json = "[null, {}, 7, "
                + listing("ABCDEFGH", "9.0.0", "Other game", "fabric", "1.22", "listed") + ","
                + listing("ABCDEFGH", "8.0.0", "Other loader", "forge", "1.21.11", "listed") + ","
                + listing("ABCDEFGH", "7.0.0", "Draft", "fabric", "1.21.11", "draft") + ","
                + listing("ABCDEFGH", "6.0.0", "Archived", "fabric", "1.21.11", "archived") + ","
                + listing("../evil", "5.0.0", "Bad URL", "fabric", "1.21.11", "listed") + ","
                + listing("not-semantic") + "," + listing("0.1.9") + "]";
        assertEquals("0.1.9", ModrinthUpdateLookup.newerRelease(json, "0.1.8", "1.21.11")
                .orElseThrow().versionNumber());
    }

    @Test
    void sanitizesNamesAndBoundsTheirLength() {
        var json = "[" + listing("ABCDEFGH", "0.1.9", "\\nRALLE\\u202e\\u00a7 " + "x".repeat(200),
                "fabric", "1.21.11", "listed") + "]";
        var name = ModrinthUpdateLookup.newerRelease(json, "0.1.8", "1.21.11").orElseThrow().name();
        assertEquals("RALLE " + "x".repeat(114), name);
        assertTrue(ModrinthUpdateLookup.newerRelease("[" + listing("ABCDEFGH", "0.1.9", "\\n\\u202e",
                "fabric", "1.21.11", "listed") + "]", "0.1.8", "1.21.11").isEmpty());
    }

    @Test
    void requestUsesPublicApiCompatibilityFiltersAndIdentifyingUserAgentWithoutCredentials() {
        var request = new ModrinthUpdateLookup("0.1.8", "1.21.11").request();
        assertEquals("api.modrinth.com", request.uri().getHost());
        assertEquals("/v2/project/ralle/version", request.uri().getPath());
        assertTrue(request.uri().getQuery().contains("loaders=[\"fabric\"]"));
        assertTrue(request.uri().getQuery().contains("game_versions=[\"1.21.11\"]"));
        assertTrue(request.uri().getQuery().contains("include_changelog=false"));
        assertTrue(request.headers().firstValue("User-Agent").orElseThrow().contains("RALLE/0.1.8"));
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertEquals(12, request.timeout().orElseThrow().toSeconds());
    }
}
