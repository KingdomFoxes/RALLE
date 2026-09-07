package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WynntilsCompatibilityTest {
    @Test
    void acceptsThePinnedReleaseAndNewerReleases() {
        assertEquals(WynntilsCompatibility.Status.MISSING, WynntilsCompatibility.status(false, ""));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED,
                WynntilsCompatibility.status(true, WynntilsCompatibility.SUPPORTED_VERSION));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED,
                WynntilsCompatibility.status(true, "v" + WynntilsCompatibility.SUPPORTED_VERSION));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED, WynntilsCompatibility.status(true, "4.2.8"));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED, WynntilsCompatibility.status(true, "4.2.10"));
        assertEquals(WynntilsCompatibility.Status.SUPPORTED, WynntilsCompatibility.status(true, "5.0.0"));
        assertEquals(WynntilsCompatibility.Status.UNSUPPORTED, WynntilsCompatibility.status(true, "4.2.6"));
        assertEquals(WynntilsCompatibility.Status.UNSUPPORTED, WynntilsCompatibility.status(true, "4.1.99"));
        assertEquals(WynntilsCompatibility.Status.UNSUPPORTED, WynntilsCompatibility.status(true, "not-a-version"));
    }
}
