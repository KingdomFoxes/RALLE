package org.kingdomfoxes.ralle.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.kingdomfoxes.ralle.RalleClient;

import java.util.EnumMap;
import java.util.Map;

public final class RalleSoundEvents {
    private static final Map<RalleSoundCue, SoundEvent> EVENTS = createEvents();
    private static boolean registered;

    private RalleSoundEvents() {}

    public static void register() {
        if (registered) return;
        EVENTS.values().forEach(event -> Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event));
        registered = true;
    }

    public static SoundEvent event(RalleSoundCue cue) {
        return EVENTS.get(cue);
    }

    private static Map<RalleSoundCue, SoundEvent> createEvents() {
        var events = new EnumMap<RalleSoundCue, SoundEvent>(RalleSoundCue.class);
        events.put(RalleSoundCue.XYLOPHONE_D5, event("ui.xylophone.d5"));
        events.put(RalleSoundCue.XYLOPHONE_E5, event("ui.xylophone.e5"));
        events.put(RalleSoundCue.XYLOPHONE_F_SHARP_5, event("ui.xylophone.f_sharp_5"));
        events.put(RalleSoundCue.XYLOPHONE_G5, event("ui.xylophone.g5"));
        events.put(RalleSoundCue.XYLOPHONE_A5, event("ui.xylophone.a5"));
        events.put(RalleSoundCue.XYLOPHONE_B5, event("ui.xylophone.b5"));
        events.put(RalleSoundCue.XYLOPHONE_C_SHARP_6, event("ui.xylophone.c_sharp_6"));
        events.put(RalleSoundCue.XYLOPHONE_D6, event("ui.xylophone.d6"));
        events.put(RalleSoundCue.XYLOPHONE_E6, event("ui.xylophone.e6"));
        events.put(RalleSoundCue.XYLOPHONE_F_SHARP_6, event("ui.xylophone.f_sharp_6"));
        events.put(RalleSoundCue.CHAT_COPY_SUCCESS, event("ui.chat_copy_success"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_D3, event("ui.acoustic_guitar.d3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_E3, event("ui.acoustic_guitar.e3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_F_SHARP_3, event("ui.acoustic_guitar.f_sharp_3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_G3, event("ui.acoustic_guitar.g3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_A3, event("ui.acoustic_guitar.a3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_B3, event("ui.acoustic_guitar.b3"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_C_SHARP_4, event("ui.acoustic_guitar.c_sharp_4"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_D4, event("ui.acoustic_guitar.d4"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_E4, event("ui.acoustic_guitar.e4"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_F_SHARP_4, event("ui.acoustic_guitar.f_sharp_4"));
        events.put(RalleSoundCue.ACOUSTIC_GUITAR_COPY_SUCCESS, event("ui.acoustic_guitar.copy_success"));
        events.put(RalleSoundCue.BASS_GUITAR_D2, event("ui.bass_guitar.d2"));
        events.put(RalleSoundCue.BASS_GUITAR_E2, event("ui.bass_guitar.e2"));
        events.put(RalleSoundCue.BASS_GUITAR_F_SHARP_2, event("ui.bass_guitar.f_sharp_2"));
        events.put(RalleSoundCue.BASS_GUITAR_G2, event("ui.bass_guitar.g2"));
        events.put(RalleSoundCue.BASS_GUITAR_A2, event("ui.bass_guitar.a2"));
        events.put(RalleSoundCue.BASS_GUITAR_B2, event("ui.bass_guitar.b2"));
        events.put(RalleSoundCue.BASS_GUITAR_C_SHARP_3, event("ui.bass_guitar.c_sharp_3"));
        events.put(RalleSoundCue.BASS_GUITAR_D3, event("ui.bass_guitar.d3"));
        events.put(RalleSoundCue.BASS_GUITAR_E3, event("ui.bass_guitar.e3"));
        events.put(RalleSoundCue.BASS_GUITAR_F_SHARP_3, event("ui.bass_guitar.f_sharp_3"));
        events.put(RalleSoundCue.BASS_GUITAR_COPY_SUCCESS, event("ui.bass_guitar.copy_success"));
        events.put(RalleSoundCue.PIANO_D4, event("ui.piano.d4"));
        events.put(RalleSoundCue.PIANO_E4, event("ui.piano.e4"));
        events.put(RalleSoundCue.PIANO_F_SHARP_4, event("ui.piano.f_sharp_4"));
        events.put(RalleSoundCue.PIANO_G4, event("ui.piano.g4"));
        events.put(RalleSoundCue.PIANO_A4, event("ui.piano.a4"));
        events.put(RalleSoundCue.PIANO_B4, event("ui.piano.b4"));
        events.put(RalleSoundCue.PIANO_C_SHARP_5, event("ui.piano.c_sharp_5"));
        events.put(RalleSoundCue.PIANO_D5, event("ui.piano.d5"));
        events.put(RalleSoundCue.PIANO_E5, event("ui.piano.e5"));
        events.put(RalleSoundCue.PIANO_F_SHARP_5, event("ui.piano.f_sharp_5"));
        events.put(RalleSoundCue.PIANO_COPY_SUCCESS, event("ui.piano.copy_success"));
        events.put(RalleSoundCue.DRUMS_BASS_DRUM, event("ui.drums.bass_drum"));
        events.put(RalleSoundCue.DRUMS_FLOOR_TOM, event("ui.drums.floor_tom"));
        events.put(RalleSoundCue.DRUMS_LOW_TOM, event("ui.drums.low_tom"));
        events.put(RalleSoundCue.DRUMS_HIGH_TOM, event("ui.drums.high_tom"));
        events.put(RalleSoundCue.DRUMS_SNARE, event("ui.drums.snare"));
        events.put(RalleSoundCue.DRUMS_CRASH, event("ui.drums.crash"));
        events.put(RalleSoundCue.LFG_TOAST_IN, event("lfg.toast_in"));
        events.put(RalleSoundCue.LFG_TOAST_OUT, event("lfg.toast_out"));
        events.put(RalleSoundCue.LFG_RESONATE_1, event("lfg.resonate_1"));
        events.put(RalleSoundCue.LFG_RESONATE_2, event("lfg.resonate_2"));
        events.put(RalleSoundCue.LFG_RESONATE_3, event("lfg.resonate_3"));
        events.put(RalleSoundCue.LFG_RESONATE_4, event("lfg.resonate_4"));
        events.put(RalleSoundCue.LFG_FUNGUS_BREAK_4, event("lfg.fungus_break_4"));
        events.put(RalleSoundCue.KICK_EXPLOSION, event("ui.kick_explosion"));
        return Map.copyOf(events);
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, path));
    }
}
