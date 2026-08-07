package org.kingdomfoxes.ralle.lfg.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LfgNoteTextTest {
    @Test
    void submissionRemovesFormattingControlsAndNormalizesWhitespace() {
        assertEquals("Hello world", LfgNoteText.sanitizeForSubmission(
                "  \u00a7cHello\n\u202eworld  "));
        assertNull(LfgNoteText.sanitizeForSubmission("\u00a7c\u202e\n"));
        assertThrows(IllegalArgumentException.class,
                () -> LfgNoteText.sanitizeForSubmission("x".repeat(81)));
    }

    @Test
    void wireValuesRejectFormattingControlsAndMalformedUnicode() {
        assertDoesNotThrow(() -> LfgNoteText.requireValidWireValue("chill run", "lobby.note"));
        assertThrows(LfgProtocolException.class,
                () -> LfgNoteText.requireValidWireValue("\u00a7cpretend", "lobby.note"));
        assertThrows(LfgProtocolException.class,
                () -> LfgNoteText.requireValidWireValue("safe\u202etxt", "lobby.note"));
        assertThrows(LfgProtocolException.class,
                () -> LfgNoteText.requireValidWireValue("broken\ud800", "lobby.note"));
    }

    @Test
    void displayGuardCapsTextWithoutSplittingSurrogatePairs() {
        var guarded = LfgNoteText.sanitizeForDisplay("a".repeat(79) + "\ud83d\ude80tail");

        assertEquals("a".repeat(79), guarded);
        assertFalse(Character.isHighSurrogate(guarded.charAt(guarded.length() - 1)));
    }
}
