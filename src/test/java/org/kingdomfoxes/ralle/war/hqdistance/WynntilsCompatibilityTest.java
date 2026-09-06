package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WynntilsCompatibilityTest {
    @Test
    void acceptsOnlyThePinnedRelease() {
        assertEquals(WynntilsCompatibility.Status.MISSING, WynntilsCompatibility.status(false, ""));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED,
                WynntilsCompatibility.status(true, WynntilsCompatibility.SUPPORTED_VERSION));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED,
                WynntilsCompatibility.status(true, "v" + WynntilsCompatibility.SUPPORTED_VERSION));
        assertEquals(WynntilsCompatibility.Status.UNSUPPORTED, WynntilsCompatibility.status(true, "4.2.8"));
    }
}
