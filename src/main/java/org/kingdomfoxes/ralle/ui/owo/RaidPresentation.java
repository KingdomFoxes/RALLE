package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

/** Shared client presentation identity for each supported Raid LFG type. */
public final class RaidPresentation {
    private RaidPresentation() {}

    public static Item item(LfgProtocol.RaidType raid) {
        return switch (raid) {
            case DAILIES -> Items.BUNDLE;
            case NOTG -> Items.SALMON;
            case NOL -> Items.OAK_SAPLING;
            case TCC -> Items.CLAY_BALL;
            case TNA -> Items.ENDER_PEARL;
            case TWP -> Items.FIRE_CHARGE;
        };
    }

    public static String shortName(LfgProtocol.RaidType raid) {
        return raid == LfgProtocol.RaidType.DAILIES ? "Dailies" : raid.name();
    }

    public static String name(LfgProtocol.RaidType raid) {
        return switch (raid) {
            case DAILIES -> "Dailies";
            case NOTG -> "Nest of the Grootslangs";
            case NOL -> "Orphion's Nexus of Light";
            case TCC -> "The Canyon Colossus";
            case TNA -> "The Nameless Anomaly";
            case TWP -> "The Wartorn Palace";
        };
    }
}
