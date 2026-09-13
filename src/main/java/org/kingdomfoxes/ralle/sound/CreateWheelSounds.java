package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Direct vanilla feedback for the explicitly enabled Create wheel. */
public final class CreateWheelSounds {
    private CreateWheelSounds() {}

    public static void hover(Minecraft minecraft) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VAULT_STEP, 1f, .55f));
    }

    /** Measure the actual randomly selected attack sample, including resource-pack replacements. */
    public static long created(Minecraft minecraft) {
        var cue = SimpleSoundInstance.forUI(SoundEvents.CONDUIT_ATTACK_TARGET, 1f, .8f);
        minecraft.getSoundManager().play(cue);
        var sound = cue.getSound();
        if (sound != null) {
            var resource = minecraft.getResourceManager().getResource(sound.getPath());
            if (resource.isPresent()) {
                try (var input = resource.get().open()) {
                    long duration = durationMillis(input.readNBytes(8 * 1024 * 1024));
                    if (duration > 0) return Math.round(duration / Math.max(.01f, cue.getPitch()));
                } catch (IOException | RuntimeException ignored) {
                    // A missing/malformed replacement must never break successful creation.
                }
            }
        }
        return 3219; // Vanilla attack1; also keeps the visual feedback usable with sound disabled.
    }

    static long durationMillis(byte[] bytes) {
        var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int rate = 0;
        long samples = 0;
        for (int at = 0; at + 27 <= bytes.length;) {
            if (data.getInt(at) != 0x5367674f) return 0; // OggS
            int segments = Byte.toUnsignedInt(bytes[at + 26]);
            int body = at + 27 + segments;
            if (body > bytes.length) return 0;
            int size = 0;
            for (int i = at + 27; i < body; i++) size += Byte.toUnsignedInt(bytes[i]);
            if (body + size > bytes.length) return 0;
            if (rate == 0 && size >= 16 && bytes[body] == 1
                    && bytes[body + 1] == 'v' && bytes[body + 2] == 'o'
                    && bytes[body + 3] == 'r' && bytes[body + 4] == 'b'
                    && bytes[body + 5] == 'i' && bytes[body + 6] == 's') {
                rate = data.getInt(body + 12);
            }
            samples = Math.max(samples, data.getLong(at + 6));
            at = body + size;
        }
        return rate > 0 ? (long) Math.ceil(samples * 1000d / rate) : 0;
    }
}
