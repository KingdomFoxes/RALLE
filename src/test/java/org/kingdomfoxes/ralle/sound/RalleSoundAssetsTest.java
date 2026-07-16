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

    private static java.io.InputStream resource(String path) {
        var input = RalleSoundAssetsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return input;
    }
}
