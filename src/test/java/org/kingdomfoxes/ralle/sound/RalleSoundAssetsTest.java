package org.kingdomfoxes.ralle.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleSoundAssetsTest {
    private static final List<String> ASSET_NAMES = List.of(
            "d5",
            "e5",
            "f_sharp_5",
            "g5",
            "a5",
            "b5",
            "c_sharp_6",
            "d6",
            "e6",
            "f_sharp_6",
            "chat_copy_success"
    );

    @Test
    void everyRegisteredCueHasAPackagedOggVorbisAsset() throws IOException {
        String manifest;
        try (var input = resource("assets/ralle/sounds.json")) {
            manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String assetName : ASSET_NAMES) {
            String resourcePath = "assets/ralle/sounds/ui/xylophone/" + assetName + ".ogg";
            try (var input = resource(resourcePath)) {
                assertArrayEquals("OggS".getBytes(StandardCharsets.US_ASCII), input.readNBytes(4), resourcePath);
            }
            assertTrue(manifest.contains("ralle:ui/xylophone/" + assetName), assetName);
        }
    }

    @Test
    void newInstrumentBanksHaveManifestEntriesAndPackagedVorbis() throws IOException {
        com.google.gson.JsonObject manifest;
        try (var input = resource("assets/ralle/sounds.json")) {
            manifest = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (var bank : List.of(ChatSelectionInstrument.ACOUSTIC_GUITAR, ChatSelectionInstrument.BASS_GUITAR,
                ChatSelectionInstrument.PIANO, ChatSelectionInstrument.DRUMS)) {
            var cues = new java.util.HashSet<RalleSoundCue>();
            for (int count = 1; count <= 10; count++) cues.add(bank.cueForCount(count));
            cues.add(bank.copySuccess());
            for (var cue : cues) {
                String bankName = bank.name().toLowerCase(java.util.Locale.ROOT);
                String suffix = cue.name().substring(bank.name().length() + 1).toLowerCase(java.util.Locale.ROOT);
                String key = "ui." + bankName + "." + suffix;
                assertTrue(manifest.has(key), key);
                String asset = manifest.getAsJsonObject(key).getAsJsonArray("sounds").get(0)
                        .getAsJsonObject().get("name").getAsString();
                String path = "assets/ralle/sounds/" + asset.substring("ralle:".length()) + ".ogg";
                try (var input = resource(path)) {
                    var bytes = input.readAllBytes();
                    assertArrayEquals("OggS".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(bytes, 4), path);
                    assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("vorbis"), path);
                }
            }
        }
    }

    @Test
    void lfgNotificationCuesReferenceExactMinecraftAssets() throws IOException {
        String manifest;
        try (var input = resource("assets/ralle/sounds.json")) {
            manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (var asset : List.of(
                "minecraft:ui/toast/in",
                "minecraft:ui/toast/out",
                "minecraft:block/amethyst/resonate1",
                "minecraft:block/amethyst/resonate2",
                "minecraft:block/amethyst/resonate3",
                "minecraft:block/amethyst/resonate4",
                "minecraft:block/fungus/break4"
        )) {
            assertTrue(manifest.contains(asset), asset);
        }
    }

    private static java.io.InputStream resource(String path) {
        var input = RalleSoundAssetsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return input;
    }
}
