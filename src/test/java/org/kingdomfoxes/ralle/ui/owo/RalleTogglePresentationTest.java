package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleTogglePresentationTest {
    @Test
    void largerTrackRemainsCenteredInTheLegacyControlLane() {
        assertEquals(126, RalleTogglePresentation.CONTROL_LANE_WIDTH);
        assertEquals(64, RalleTogglePresentation.TRACK_WIDTH);
        assertEquals(14, RalleTogglePresentation.TRACK_HEIGHT);
        assertEquals(31, (RalleTogglePresentation.CONTROL_LANE_WIDTH - RalleTogglePresentation.TRACK_WIDTH) / 2);
    }

    @Test
    void movingThumbIsLargerThanTheTrackInterior() {
        assertEquals(14, RalleTogglePresentation.THUMB_WIDTH);
        assertEquals(18, RalleTogglePresentation.THUMB_HEIGHT);
        assertEquals(11, RalleTogglePresentation.thumbTop(10, RalleTogglePresentation.CONTROL_HEIGHT));
    }

    @Test
    void thumbOccupiesOppositeTrackEnds() {
        int off = RalleTogglePresentation.thumbLeft(10, RalleTogglePresentation.TRACK_WIDTH, false);
        int on = RalleTogglePresentation.thumbLeft(10, RalleTogglePresentation.TRACK_WIDTH, true);

        assertEquals(12, off);
        assertEquals(10 + RalleTogglePresentation.TRACK_WIDTH - RalleTogglePresentation.THUMB_WIDTH - 2, on);
        assertTrue(on > off);
    }

    @Test
    void accessibleStateUsesNonColorTranslation() {
        assertEquals("ralle.settings.disabled", RalleTogglePresentation.stateTranslationKey(false));
        assertEquals("ralle.settings.enabled", RalleTogglePresentation.stateTranslationKey(true));
    }
}
