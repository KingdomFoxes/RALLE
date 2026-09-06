package org.kingdomfoxes.ralle.war.hqdistance;

import com.wynntils.screens.maps.GuildMapScreen;
import com.wynntils.services.map.pois.TerritoryPoi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Exact-version integration kept separate from calculation, state, settings, and rendering contracts. */
final class WynntilsHqDistanceIntegration {
    private static final TerritoryLabelRenderer RENDERER = new MinecraftTerritoryLabelRenderer();
    private WynntilsHqDistanceIntegration() {}

    static void render(
            Object candidate,
            GuiGraphics graphics,
            float renderX,
            float renderY,
            float zoomRenderScale,
            HqInspectionService service
    ) {
        if (!(Minecraft.getInstance().screen instanceof GuildMapScreen)) return;
        if (!(candidate instanceof TerritoryPoi poi)) return;
        var profile = poi.getTerritoryProfile();
        if (profile == null) return;
        String timerName = profile.getFriendlyName();
        if (!HqInspectionVisibility.visible(true, true, true, true, service.hasActiveAttackTimer(timerName))) return;

        float width = (profile.getEndX() - profile.getStartX()) * zoomRenderScale;
        float height = (profile.getEndZ() - profile.getStartZ()) * zoomRenderScale;
        var info = poi.getTerritoryInfo();
        RENDERER.render(
                graphics,
                service.inspect(profile.getName()),
                new TerritoryRenderBounds(renderX - width / 2f, renderY - height / 2f, width, height),
                info != null && !poi.isFakeTerritoryInfo() && info.isHeadquarters());
    }
}
