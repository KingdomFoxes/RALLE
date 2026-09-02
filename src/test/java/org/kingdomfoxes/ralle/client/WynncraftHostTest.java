package org.kingdomfoxes.ralle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynncraftHostTest {
    @Test void acceptsRealDomainsSubdomainsPortsAndTrailingDots() {
        assertTrue(WynncraftHost.matches("wynncraft.com"));
        assertTrue(WynncraftHost.matches("PLAY.WYNNCRAFT.COM:25565"));
        assertTrue(WynncraftHost.matches("eu.wynncraft.net."));
    }

    @Test void rejectsLookalikesAndMalformedAddresses() {
        assertFalse(WynncraftHost.matches("notwynncraft.com"));
        assertFalse(WynncraftHost.matches("wynncraft.com.example.org"));
        assertFalse(WynncraftHost.matches("wynncraft.net.evil.test:25565"));
        assertFalse(WynncraftHost.matches(null));
    }
}
