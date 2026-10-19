package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ClassTransitionPixelsTest {
    @Test void replaysAnonymizedMasksFromTheProvidedRecordingIncludingInitialAndDestinationBlackouts() throws Exception {
        try (var input = getClass().getResourceAsStream("/classes/transition-video-samples.json")) {
            assertNotNull(input);
            var fixture = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : fixture.getAsJsonArray("samples")) {
                var sample = entry.getAsJsonObject();
                var rgba = solid(255);
                byte[] mask = Base64.getDecoder().decode(sample.get("darkMask").getAsString());
                int bit = 0;
                for (int y = 9; y < 27; y++) for (int x = 16; x < 48; x++, bit++)
                    pixel(rgba, x, y, (mask[bit / 8] & 1 << bit % 8) != 0 ? 0 : 255);
                assertEquals(sample.get("black").getAsBoolean(), ClassTransitionPixels.black(rgba, 64, 36),
                        "Video at " + sample.get("timeSeconds"));
            }
        }
    }

    @Test void edgeHudsAndCrosshairDoNotHideBlackButDarkGameplayIsNotEnough() {
        var rgba = solid(0);
        for (int x = 0; x < 64; x++) { pixel(rgba, x, 2, 255); pixel(rgba, x, 33, 255); }
        for (int y = 17; y <= 19; y++) for (int x = 31; x <= 33; x++) pixel(rgba, x, y, 255);
        assertTrue(ClassTransitionPixels.black(rgba, 64, 36));
        for (int x = 20; x < 45; x++) pixel(rgba, x, 15, 12);
        assertFalse(ClassTransitionPixels.black(rgba, 64, 36));
        assertFalse(ClassTransitionPixels.black(solid(16), 64, 36));
        assertFalse(ClassTransitionPixels.black(solid(255), 64, 36));
        assertFalse(ClassTransitionPixels.black(ByteBuffer.allocate(1), 64, 36));
    }

    @Test void blackPixelDetectionPreservesBufferPositionAndSupportsMappedSlices() {
        var parent = ByteBuffer.allocate(64 * 36 * 4 + 16);
        parent.position(16);
        var rgba = parent.slice();
        assertTrue(ClassTransitionPixels.black(rgba, 64, 36));
        assertEquals(0, rgba.position());
        assertEquals(16, parent.position());
    }

    private static ByteBuffer solid(int value) {
        var rgba = ByteBuffer.allocate(64 * 36 * 4);
        for (int y = 0; y < 36; y++) for (int x = 0; x < 64; x++) pixel(rgba, x, y, value);
        return rgba;
    }
    private static void pixel(ByteBuffer rgba, int x, int y, int rgb) {
        int index = (y * 64 + x) * 4;
        for (int channel = 0; channel < 3; channel++) rgba.put(index + channel, (byte) rgb);
        rgba.put(index + 3, (byte) 255);
    }
}
