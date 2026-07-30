package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidPresentationTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allRaidNamesAndItemsUseTheSharedApprovedMapping() {
        assertEquals(Items.BUNDLE, RaidPresentation.item(LfgProtocol.RaidType.DAILIES));
        assertEquals(Items.SALMON, RaidPresentation.item(LfgProtocol.RaidType.NOTG));
        assertEquals(Items.OAK_SAPLING, RaidPresentation.item(LfgProtocol.RaidType.NOL));
        assertEquals(Items.CLAY_BALL, RaidPresentation.item(LfgProtocol.RaidType.TCC));
        assertEquals(Items.ENDER_PEARL, RaidPresentation.item(LfgProtocol.RaidType.TNA));
        assertEquals(Items.FIRE_CHARGE, RaidPresentation.item(LfgProtocol.RaidType.TWP));

        assertEquals("Dailies", RaidPresentation.shortName(LfgProtocol.RaidType.DAILIES));
        assertEquals("The Nameless Anomaly", RaidPresentation.name(LfgProtocol.RaidType.TNA));
    }
}
