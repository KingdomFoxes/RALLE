package org.kingdomfoxes.ralle.war.hqdistance.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.kingdomfoxes.ralle.war.hqdistance.HqDistanceOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.wynntils.services.map.pois.TerritoryPoi", remap = false)
abstract class TerritoryPoiMixin {
    @Inject(method = "renderAt", at = @At("TAIL"), require = 0, remap = false)
    private void ralle$renderHqDistance(
            GuiGraphics graphics,
            float renderX,
            float renderY,
            boolean hovered,
            float scale,
            float zoomRenderScale,
            float zoomLevel,
            boolean showLabels,
            CallbackInfo callbackInfo
    ) {
        HqDistanceOverlay.render(this, graphics, renderX, renderY, hovered, zoomRenderScale);
    }
}
