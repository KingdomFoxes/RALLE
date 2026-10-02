package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Avatar;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.settings.RalleSettings;

/** Only changes local visibility; vanilla still extracts and submits the server's name and score. */
public final class OwnNameTagVisibility {
    private OwnNameTagVisibility() {}

    public static boolean enabled() {
        return RalleClient.initialized()
                && RalleClient.context().settings().setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class).value();
    }

    public static boolean show(Avatar avatar) {
        if (!enabled()) return false;
        var client = Minecraft.getInstance();
        if (avatar != client.player || avatar.isInvisibleTo(client.player) || avatar.isVehicle()) return false;
        if (OwnNameTagPreview.active()) {
            return client.screen instanceof InventoryScreen || client.screen instanceof CreativeModeInventoryScreen;
        }
        return Minecraft.renderNames() && client.getCameraEntity() == client.player
                && !client.options.getCameraType().isFirstPerson();
    }
}
