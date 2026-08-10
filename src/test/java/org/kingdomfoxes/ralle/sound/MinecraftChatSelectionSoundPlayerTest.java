package org.kingdomfoxes.ralle.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftChatSelectionSoundPlayerTest {
    @Test
    void mapsLogicalMessageCountsToTheCappedDMajorAscent() {
        assertEquals(List.of(
                        RalleSoundCue.XYLOPHONE_D5,
                        RalleSoundCue.XYLOPHONE_E5,
                        RalleSoundCue.XYLOPHONE_F_SHARP_5,
                        RalleSoundCue.XYLOPHONE_G5,
                        RalleSoundCue.XYLOPHONE_A5,
                        RalleSoundCue.XYLOPHONE_B5,
                        RalleSoundCue.XYLOPHONE_C_SHARP_6,
                        RalleSoundCue.XYLOPHONE_D6,
                        RalleSoundCue.XYLOPHONE_E6,
                        RalleSoundCue.XYLOPHONE_F_SHARP_6,
                        RalleSoundCue.XYLOPHONE_F_SHARP_6
                ), java.util.stream.IntStream.rangeClosed(1, 11)
                        .mapToObj(MinecraftChatSelectionSoundPlayer::cueForCount)
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> MinecraftChatSelectionSoundPlayer.cueForCount(0));
    }

    @Test
    void rateLimitsAndCoalescesRapidChangesToTheLatestCount() {
        var clock = new MutableClock();
        var output = new ArrayList<RalleSoundCue>();
        var player = player(() -> true, output, clock);

        player.playSelectionCount(1);
        clock.now = 10;
        player.playSelectionCount(2);
        clock.now = 25;
        player.playSelectionCount(4);
        clock.now = 39;
        player.tick();
        clock.now = 40;
        player.tick();

        assertEquals(List.of(RalleSoundCue.XYLOPHONE_D5, RalleSoundCue.XYLOPHONE_G5), output);
    }

    @Test
    void resetDropsPendingFeedbackAndMakesTheNextInitialHitImmediate() {
        var clock = new MutableClock();
        var output = new ArrayList<RalleSoundCue>();
        var player = player(() -> true, output, clock);

        player.playSelectionCount(1);
        clock.now = 10;
        player.playSelectionCount(2);
        player.resetSelection();
        clock.now = 11;
        player.playSelectionCount(1);
        clock.now = 100;
        player.tick();

        assertEquals(List.of(RalleSoundCue.XYLOPHONE_D5, RalleSoundCue.XYLOPHONE_D5), output);
    }

    @Test
    void successfulCopyIsImmediateAndClearsPendingSelectionFeedback() {
        var clock = new MutableClock();
        var output = new ArrayList<RalleSoundCue>();
        var player = player(() -> true, output, clock);

        player.playSelectionCount(1);
        clock.now = 10;
        player.playSelectionCount(2);
        player.playCopySuccess();
        clock.now = 100;
        player.tick();

        assertEquals(List.of(RalleSoundCue.XYLOPHONE_D5, RalleSoundCue.CHAT_COPY_SUCCESS), output);
    }

    @Test
    void disabledGateSuppressesHitsMotifsAndPendingPlayback() {
        var enabled = new AtomicBoolean(false);
        var clock = new MutableClock();
        var output = new ArrayList<RalleSoundCue>();
        var player = player(enabled::get, output, clock);

        player.playSelectionCount(1);
        player.playCopySuccess();
        enabled.set(true);
        player.playSelectionCount(1);
        clock.now = 10;
        player.playSelectionCount(2);
        enabled.set(false);
        clock.now = 100;
        player.tick();

        assertEquals(List.of(RalleSoundCue.XYLOPHONE_D5), output);
    }

    @Test
    void soundSettingRemainsInertUntilScreenshottingIsEnabled(@TempDir Path temporaryDirectory) {
        var settings = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(settings);
        var gate = MinecraftChatSelectionSoundPlayer.soundGate(settings);

        settings.setting("chat-selection-sounds", BooleanSetting.class).set(true);
        assertFalse(gate.getAsBoolean());
        settings.setting("chat-screenshot-enabled", BooleanSetting.class).set(true);
        assertTrue(gate.getAsBoolean());
        settings.setting("chat-screenshot-enabled", BooleanSetting.class).set(false);
        assertFalse(gate.getAsBoolean());
    }

    private static MinecraftChatSelectionSoundPlayer player(
            java.util.function.BooleanSupplier enabled,
            List<RalleSoundCue> output,
            MutableClock clock
    ) {
        return new MinecraftChatSelectionSoundPlayer(enabled, output::add, clock::get);
    }

    private static final class MutableClock {
        long now;

        long get() {
            return now;
        }
    }
}
