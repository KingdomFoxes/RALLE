package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Plays gated LFG presentation cues plus the deliberately quiet host-control feedback. */
public final class MinecraftLfgSoundPlayer implements LfgSoundPlayer {
    private final Executor clientExecutor;
    private final BooleanSupplier lfgSounds;
    private final Consumer<SoundInstance> playback;

    public MinecraftLfgSoundPlayer(Minecraft minecraft, SettingsRegistry settings) {
        this(minecraft::execute, () -> settings.setting("raid-lfg-enabled", BooleanSetting.class).value()
                        && settings.setting("notification-sounds", BooleanSetting.class).value(),
                sound -> minecraft.getSoundManager().play(sound));
    }

    MinecraftLfgSoundPlayer(Executor clientExecutor, BooleanSupplier lfgSounds, Consumer<SoundInstance> playback) {
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.lfgSounds = Objects.requireNonNull(lfgSounds);
        this.playback = Objects.requireNonNull(playback);
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
        play(SoundEvents.BELL_BLOCK, 1.0F, 0.7F, true);
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
        play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.8F, true);
    }

    @Override
    public void playPartyLeft() {
        play(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0F, 0.8F, true);
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
        play(RalleSoundEvents.event(cue), 1.0F, 0.8F, true);
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        play(sound, pitch, volume, false);
    }

    private void play(SoundEvent sound, float pitch, float volume, boolean gated) {
        // Store observers and REST/live-event completions can run on network workers.
        // SoundEngine owns ordinary HashMaps: never construct/play a cue on those workers.
        clientExecutor.execute(() -> {
            if (!gated || lfgSounds.getAsBoolean()) {
                playback.accept(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        });
    }

    private void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }
}
