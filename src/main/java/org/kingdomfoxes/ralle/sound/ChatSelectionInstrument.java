package org.kingdomfoxes.ralle.sound;

import java.util.Arrays;
import java.util.List;

import static org.kingdomfoxes.ralle.sound.RalleSoundCue.*;

/** Local chat feedback banks. Counts cap at the final hit; each melodic bank uses its own register. */
public enum ChatSelectionInstrument {
    XYLOPHONE("xylophone", CHAT_COPY_SUCCESS,
            XYLOPHONE_D5, XYLOPHONE_E5, XYLOPHONE_F_SHARP_5,
            XYLOPHONE_G5, XYLOPHONE_A5, XYLOPHONE_B5,
            XYLOPHONE_C_SHARP_6, XYLOPHONE_D6, XYLOPHONE_E6,
            XYLOPHONE_F_SHARP_6),
    ACOUSTIC_GUITAR("acoustic-guitar", ACOUSTIC_GUITAR_COPY_SUCCESS,
            ACOUSTIC_GUITAR_D3, ACOUSTIC_GUITAR_E3, ACOUSTIC_GUITAR_F_SHARP_3,
            ACOUSTIC_GUITAR_G3, ACOUSTIC_GUITAR_A3, ACOUSTIC_GUITAR_B3,
            ACOUSTIC_GUITAR_C_SHARP_4, ACOUSTIC_GUITAR_D4, ACOUSTIC_GUITAR_E4,
            ACOUSTIC_GUITAR_F_SHARP_4),
    BASS_GUITAR("bass-guitar", BASS_GUITAR_COPY_SUCCESS,
            BASS_GUITAR_D2, BASS_GUITAR_E2, BASS_GUITAR_F_SHARP_2,
            BASS_GUITAR_G2, BASS_GUITAR_A2, BASS_GUITAR_B2,
            BASS_GUITAR_C_SHARP_3, BASS_GUITAR_D3, BASS_GUITAR_E3,
            BASS_GUITAR_F_SHARP_3),
    PIANO("piano", PIANO_COPY_SUCCESS,
            PIANO_D4, PIANO_E4, PIANO_F_SHARP_4, PIANO_G4, PIANO_A4, PIANO_B4, PIANO_C_SHARP_5, PIANO_D5, PIANO_E5, PIANO_F_SHARP_5),
    PIANO_RECORDED("piano-recorded", PIANO_RECORDED_COPY_SUCCESS,
            PIANO_RECORDED_D4, PIANO_RECORDED_E4, PIANO_RECORDED_F_SHARP_4, PIANO_RECORDED_G4,
            PIANO_RECORDED_A4, PIANO_RECORDED_B4, PIANO_RECORDED_C_SHARP_5, PIANO_RECORDED_D5,
            PIANO_RECORDED_E5, PIANO_RECORDED_F_SHARP_5),
    DRUMS("drums", DRUMS_CRASH,
            DRUMS_BASS_DRUM, DRUMS_FLOOR_TOM, DRUMS_LOW_TOM, DRUMS_HIGH_TOM, DRUMS_SNARE),
    DRUMS_RECORDED("drums-recorded", DRUMS_RECORDED_CRASH,
            DRUMS_RECORDED_BASS_DRUM, DRUMS_RECORDED_FLOOR_TOM, DRUMS_RECORDED_LOW_TOM,
            DRUMS_RECORDED_HIGH_TOM, DRUMS_RECORDED_SNARE);

    private final String settingValue;
    private final RalleSoundCue copySuccess;
    private final List<RalleSoundCue> selectionCues;

    ChatSelectionInstrument(String settingValue, RalleSoundCue copySuccess, RalleSoundCue... selectionCues) {
        this.settingValue = settingValue;
        this.copySuccess = copySuccess;
        this.selectionCues = List.of(selectionCues);
    }

    public static ChatSelectionInstrument fromSetting(String value) {
        return Arrays.stream(values()).filter(instrument -> instrument.settingValue.equals(value))
                .findFirst().orElse(XYLOPHONE);
    }

    public RalleSoundCue cueForCount(int count) {
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        return selectionCues.get(Math.min(count, selectionCues.size()) - 1);
    }

    public RalleSoundCue copySuccess() {
        return copySuccess;
    }
}

