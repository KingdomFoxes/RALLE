package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Shared option-entry cue; successful Create effects remain owned by CreateWheelSounds. */
public final class SelectorWheelSounds {
    private SelectorWheelSounds() {}

    public static void hover(Minecraft minecraft) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VAULT_STEP, 1f, .55f));
    }
}
