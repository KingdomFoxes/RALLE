package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftRaidRegionDetectorTest {
    @Test
    void findsWynncraftRegionLabelsInServerAndScoreboardText() {
        assertEquals(LfgProtocol.Region.EU,
                MinecraftRaidRegionDetector.detect(List.of("WC Lobby", "EU10")).orElseThrow());
        assertEquals(LfgProtocol.Region.NA,
                MinecraftRaidRegionDetector.detect(List.of("World: NA-14")).orElseThrow());
        assertEquals(LfgProtocol.Region.AS,
                MinecraftRaidRegionDetector.detect(List.of("[AS 20]")).orElseThrow());
    }

    @Test
    void rejectsUnnumberedAndEmbeddedRegionFragments() {
        assertTrue(MinecraftRaidRegionDetector.detect(
                List.of("EU", "NA player", "CAS20UAL", "wynncraft.com")).isEmpty());
    }
}
