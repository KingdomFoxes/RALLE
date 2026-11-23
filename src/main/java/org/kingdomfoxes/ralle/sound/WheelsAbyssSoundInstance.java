package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping UI track for the Wheels settings abyss; starts silent and tracks scroll linearly. */
public final class WheelsAbyssSoundInstance extends AbstractTickableSoundInstance {
    public WheelsAbyssSoundInstance() {
        super(RalleSoundEvents.event(RalleSoundCue.WHEELS_ABYSS), SoundSource.UI, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0F;
        this.pitch = 1.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void setScrollVolume(float nextVolume) {
        this.volume = Math.clamp(nextVolume, 0.0F, 1.0F);
    }

    public void finish() {
        stop();
    }

    @Override
    public void tick() {
        // Volume updates are pushed immediately as the Wheels page scrolls.
    }
}
