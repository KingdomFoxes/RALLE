package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HqInspectionVisibilityTest {
    @Test
    void requiresCompleteGateAndImmediatelyYieldsToActiveTimer() {
        assertTrue(HqInspectionVisibility.visible(true, true, true, true, false));
        assertFalse(HqInspectionVisibility.visible(false, true, true, true, false));
        assertFalse(HqInspectionVisibility.visible(true, false, true, true, false));
        assertFalse(HqInspectionVisibility.visible(true, true, false, true, false));
        assertFalse(HqInspectionVisibility.visible(true, true, true, false, false));
        assertFalse(HqInspectionVisibility.visible(true, true, true, true, true));
        assertTrue(HqInspectionVisibility.visible(true, true, true, true, false));
    }

    @Test
    void eitherPhysicalControlKeyActivatesInspection() {
        assertFalse(InspectionModifier.held(false, false));
        assertTrue(InspectionModifier.held(true, false));
        assertTrue(InspectionModifier.held(false, true));
        assertTrue(InspectionModifier.held(true, true));
    }
}
