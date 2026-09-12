package org.kingdomfoxes.ralle.sound;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class CreateWheelSoundsTest {
    @Test void readsSampleRateAndFinalGranuleWithoutDecodingAudio() {
        for (long samples : new long[] {154482, 144494, 178762}) {
            byte[] bytes = new byte[72];
            var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            data.putInt(0, 0x5367674f);
            bytes[26] = 1;
            bytes[27] = 16;
            bytes[28] = 1;
            System.arraycopy("vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 29, 6);
            data.putInt(40, 48000);
            data.putInt(44, 0x5367674f);
            data.putLong(50, samples);
            assertEquals((long) Math.ceil(samples / 48d), CreateWheelSounds.durationMillis(bytes));
        }
    }

    @Test void malformedOrTruncatedAssetsHaveNoDuration() {
        assertEquals(0, CreateWheelSounds.durationMillis(new byte[0]));
        assertEquals(0, CreateWheelSounds.durationMillis(new byte[60]));
        var bytes = new byte[28];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0x5367674f);
        bytes[26] = 1;
        bytes[27] = 100;
        assertEquals(0, CreateWheelSounds.durationMillis(bytes));
    }
}
