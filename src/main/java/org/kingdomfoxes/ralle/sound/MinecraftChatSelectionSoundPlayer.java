package org.kingdomfoxes.ralle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class MinecraftChatSelectionSoundPlayer implements ChatSelectionSoundPlayer {
    static final long SELECTION_RATE_LIMIT_MILLIS = 40L;
    private static final float VOLUME = 0.35F;

    private final BooleanSupplier enabled;
    private final Consumer<RalleSoundCue> output;
    private final LongSupplier clock;

    private int pendingCount;
    private long lastSelectionPlayedAt;
    private boolean hasPlayedSelection;

    public MinecraftChatSelectionSoundPlayer(Minecraft minecraft, SettingsRegistry settings) {
        this(
                soundGate(settings),
                cue -> minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(RalleSoundEvents.event(cue), 1.0F, VOLUME)
                ),
                () -> System.nanoTime() / 1_000_000L
        );
    }

    MinecraftChatSelectionSoundPlayer(
            BooleanSupplier enabled,
            Consumer<RalleSoundCue> output,
            LongSupplier clock
    ) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.output = Objects.requireNonNull(output, "output");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void playSelectionCount(int logicalMessageCount) {
        if (logicalMessageCount < 1) throw new IllegalArgumentException("logicalMessageCount must be positive");
        if (!enabled.getAsBoolean()) {
            resetSelection();
            return;
        }

        long now = clock.getAsLong();
        if (!hasPlayedSelection || now - lastSelectionPlayedAt >= SELECTION_RATE_LIMIT_MILLIS) {
            playSelectionNow(logicalMessageCount, now);
        } else {
            pendingCount = logicalMessageCount;
        }
    }

    @Override
    public void playCopySuccess() {
        pendingCount = 0;
        if (enabled.getAsBoolean()) output.accept(RalleSoundCue.CHAT_COPY_SUCCESS);
    }

    @Override
    public void tick() {
        if (!enabled.getAsBoolean()) {
            resetSelection();
            return;
        }
        if (pendingCount == 0) return;

        long now = clock.getAsLong();
        if (now - lastSelectionPlayedAt >= SELECTION_RATE_LIMIT_MILLIS) {
            playSelectionNow(pendingCount, now);
        }
    }

    @Override
    public void resetSelection() {
        pendingCount = 0;
        hasPlayedSelection = false;
        lastSelectionPlayedAt = 0L;
    }

    static RalleSoundCue cueForCount(int logicalMessageCount) {
        return switch (Math.min(logicalMessageCount, 10)) {
            case 1 -> RalleSoundCue.XYLOPHONE_D5;
            case 2 -> RalleSoundCue.XYLOPHONE_E5;
            case 3 -> RalleSoundCue.XYLOPHONE_F_SHARP_5;
            case 4 -> RalleSoundCue.XYLOPHONE_G5;
            case 5 -> RalleSoundCue.XYLOPHONE_A5;
            case 6 -> RalleSoundCue.XYLOPHONE_B5;
            case 7 -> RalleSoundCue.XYLOPHONE_C_SHARP_6;
            case 8 -> RalleSoundCue.XYLOPHONE_D6;
            case 9 -> RalleSoundCue.XYLOPHONE_E6;
            case 10 -> RalleSoundCue.XYLOPHONE_F_SHARP_6;
            default -> throw new IllegalArgumentException("logicalMessageCount must be positive");
        };
    }

    private void playSelectionNow(int logicalMessageCount, long now) {
        pendingCount = 0;
        output.accept(cueForCount(logicalMessageCount));
        lastSelectionPlayedAt = now;
        hasPlayedSelection = true;
    }

    static BooleanSupplier soundGate(SettingsRegistry settings) {
        BooleanSetting screenshotsEnabled = settings.setting("chat-screenshot-enabled", BooleanSetting.class);
        BooleanSetting soundsEnabled = settings.setting("chat-selection-sounds", BooleanSetting.class);
        return () -> screenshotsEnabled.value() && soundsEnabled.value();
    }
}
