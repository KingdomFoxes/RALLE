package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

/** Uses deliberately vanilla cues for the first host-control sound pass. */
public final class MinecraftLfgSoundPlayer implements LfgSoundPlayer {
    private final Minecraft minecraft;
    private final BooleanSetting notificationSounds;

    public MinecraftLfgSoundPlayer(Minecraft minecraft, SettingsRegistry settings) {
        this.minecraft = minecraft;
        this.notificationSounds = settings.setting("notification-sounds", BooleanSetting.class);
    }

    @Override
    public void playKickTargetHover() {
        play(SoundEvents.NOTE_BLOCK_HAT, 1.4F, 0.25F);
    }

    @Override
    public void playKickHoldStart() {
        play(SoundEvents.CROSSBOW_LOADING_START, 1.0F, 0.55F);
    }

    @Override
    public void playKickHoldCancelled() {
        play(SoundEvents.LEVER_CLICK, 0.7F, 0.45F);
    }

    @Override
    public void playKickSucceeded() {
        play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.85F, 0.65F);
    }

    @Override
    public void playPartyPing() {
        if (notificationSounds.value()) play(SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }
}
