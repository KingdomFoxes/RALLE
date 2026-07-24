package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RalleButtonRenderersTest {
    @Test
    void countdownOverlayGrowsLeftToRightAndStaysInsideTheButtonFace() {
        assertEquals(0, RalleButtonRenderers.countdownOverlayWidth(20, 1d));
        assertEquals(10, RalleButtonRenderers.countdownOverlayWidth(20, 0.5d));
        assertEquals(20, RalleButtonRenderers.countdownOverlayWidth(20, 0d));
        assertEquals(0, RalleButtonRenderers.countdownOverlayWidth(20, 2d));
        assertEquals(20, RalleButtonRenderers.countdownOverlayWidth(20, -1d));
        assertEquals(0, RalleButtonRenderers.countdownOverlayWidth(-20, 0.5d));
    }
}
