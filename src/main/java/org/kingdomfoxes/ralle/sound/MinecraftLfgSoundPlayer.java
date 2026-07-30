package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

/** Plays gated LFG presentation cues plus the deliberately quiet host-control feedback. */
public final class MinecraftLfgSoundPlayer implements LfgSoundPlayer {
    private final Minecraft minecraft;
    private final BooleanSetting lfgSounds;

    public MinecraftLfgSoundPlayer(Minecraft minecraft, SettingsRegistry settings) {
        this.minecraft = minecraft;
        this.lfgSounds = settings.setting("notification-sounds", BooleanSetting.class);
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
    public void playPartyLocked() {
        play(SoundEvents.VAULT_INSERT_ITEM, 1.0F, 0.8F);
    }

    @Override
    public void playPartyUnlocked() {
        play(SoundEvents.VAULT_INSERT_ITEM_FAIL, 1.0F, 0.8F);
    }

    @Override
    public void playPartyPing() {
        if (lfgSounds.value()) play(SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
    }

    @Override
    public void playLocalPartyPing() {
        play(SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
    }

    @Override
    public void playNotificationIn() {
        playNotificationCue(RalleSoundCue.LFG_TOAST_IN);
    }

    @Override
    public void playNotificationOut() {
        playNotificationCue(RalleSoundCue.LFG_TOAST_OUT);
    }

    @Override
    public void playNewPartyReady() {
        playNotificationCue(RalleSoundCue.LFG_RESONATE_1);
    }

    @Override
    public void playPartyCreated() {
        playNotificationCue(RalleSoundCue.LFG_RESONATE_1);
    }

    @Override
    public void playPartyJoined() {
        if (lfgSounds.value()) play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.8F);
    }

    @Override
    public void playPartyLeft() {
        if (lfgSounds.value()) play(SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.0F, 0.8F);
    }

    @Override
    public void playRosterSlotOccupied(int occupiedSlot) {
        playNotificationCue(switch (Math.clamp(occupiedSlot, 1, 4)) {
            case 1 -> RalleSoundCue.LFG_RESONATE_1;
            case 2 -> RalleSoundCue.LFG_RESONATE_2;
            case 3 -> RalleSoundCue.LFG_RESONATE_3;
            default -> RalleSoundCue.LFG_RESONATE_4;
        });
    }

    @Override
    public void playPartyFilledRaceLost() {
        playNotificationCue(RalleSoundCue.LFG_FUNGUS_BREAK_4);
    }

    private void playNotificationCue(RalleSoundCue cue) {
        if (lfgSounds.value()) play(RalleSoundEvents.event(cue), 1.0F, 0.8F);
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }
}
