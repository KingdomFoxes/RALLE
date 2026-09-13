package org.kingdomfoxes.ralle.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.*;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ChatSelectionInstrumentTest {
    @Test
    void melodicBanksKeepTheSameIntervalsInTheirOwnRegisters() {
        var notes = List.of("D5", "E5", "F_SHARP_5", "G5", "A5", "B5", "C_SHARP_6", "D6", "E6", "F_SHARP_6");
        for (var bank : List.of(ChatSelectionInstrument.XYLOPHONE, ChatSelectionInstrument.ACOUSTIC_GUITAR,
                ChatSelectionInstrument.BASS_GUITAR, ChatSelectionInstrument.PIANO)) {
            for (int count = 1; count <= 10; count++) {
                String note = notes.get(count - 1);
                int shift = switch (bank) {
                    case ACOUSTIC_GUITAR -> 2;
                    case BASS_GUITAR -> 3;
                    case PIANO -> 1;
                    default -> 0;
                };
                note = note.replace("5", Integer.toString(5 - shift)).replace("6", Integer.toString(6 - shift));
                assertEquals(bank.name() + "_" + note, bank.cueForCount(count).name());
            }
            assertEquals(bank.cueForCount(10), bank.cueForCount(1000));
        }
    }

    @Test
    void drumsAscendToSnareAndReserveCrashForCopy() {
        assertEquals(List.of(RalleSoundCue.DRUMS_BASS_DRUM, RalleSoundCue.DRUMS_FLOOR_TOM,
                RalleSoundCue.DRUMS_LOW_TOM, RalleSoundCue.DRUMS_HIGH_TOM, RalleSoundCue.DRUMS_SNARE,
                RalleSoundCue.DRUMS_SNARE), java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(ChatSelectionInstrument.DRUMS::cueForCount).toList());
        assertEquals(RalleSoundCue.DRUMS_CRASH, ChatSelectionInstrument.DRUMS.copySuccess());
    }

    @ParameterizedTest
    @EnumSource(ChatSelectionInstrument.class)
    void everyBankCoalescesShrinkingCountsAndClearsPendingHitsOnCopyAndDisable(ChatSelectionInstrument bank) {
        var clock = new AtomicLong();
        var enabled = new AtomicBoolean(true);
        var output = new ArrayList<RalleSoundCue>();
        var player = new MinecraftChatSelectionSoundPlayer(enabled::get, () -> bank, output::add, clock::get);
        player.playSelectionCount(1);
        clock.set(10);
        player.playSelectionCount(9);
        clock.set(20);
        player.playSelectionCount(2);
        clock.set(39);
        player.tick();
        assertEquals(List.of(bank.cueForCount(1)), output);
        clock.set(40);
        player.tick();
        assertEquals(List.of(bank.cueForCount(1), bank.cueForCount(2)), output);
        player.playSelectionCount(3);
        player.playCopySuccess();
        clock.set(80);
        player.tick();
        assertEquals(List.of(bank.cueForCount(1), bank.cueForCount(2), bank.copySuccess()), output);
        player.playSelectionCount(4);
        player.playSelectionCount(5);
        enabled.set(false);
        clock.set(120);
        player.tick();
        player.playCopySuccess();
        assertEquals(4, output.size());
        enabled.set(true);
        player.tick();
        assertEquals(4, output.size());
        assertThrows(IllegalArgumentException.class, () -> bank.cueForCount(0));
    }

    @Test
    void instrumentPersistsAndIsHiddenBehindBothParentsWithSafeFallback(@TempDir Path directory) throws Exception {
        var path = directory.resolve("ralle.properties");
        var registry = registry(path);
        var choice = registry.setting(RalleSettings.CHAT_SELECTION_INSTRUMENT_ID, ChoiceSetting.class);
        assertEquals("xylophone", choice.value());
        assertEquals(List.of("xylophone", "acoustic-guitar", "bass-guitar", "piano", "drums"), choice.choices());
        assertFalse(registry.visible(choice.id()));
        registry.setting("chat-screenshot-enabled", BooleanSetting.class).set(true);
        assertFalse(registry.visible(choice.id()));
        registry.setting("chat-selection-sounds", BooleanSetting.class).set(true);
        assertTrue(registry.visible(choice.id()));
        var instrument = MinecraftChatSelectionSoundPlayer.instrumentChoice(registry);
        choice.set("bass-guitar");
        assertEquals(ChatSelectionInstrument.BASS_GUITAR, instrument.get());
        registry.setting("chat-screenshot-enabled", BooleanSetting.class).set(false);
        assertFalse(registry.visible(choice.id()));
        assertFalse(MinecraftChatSelectionSoundPlayer.soundGate(registry).getAsBoolean());
        assertEquals("bass-guitar", registry(path).setting(choice.id(), ChoiceSetting.class).value());
        assertTrue(Files.readString(path).contains("chat.chat-selection-instrument=bass-guitar"));
        Files.writeString(path, "chat.chat-selection-instrument=unknown\n");
        assertEquals("xylophone", registry(path).setting(choice.id(), ChoiceSetting.class).value());
        assertEquals(ChatSelectionInstrument.XYLOPHONE, ChatSelectionInstrument.fromSetting("unknown"));
    }

    private static SettingsRegistry registry(Path path) {
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        return registry;
    }
}
