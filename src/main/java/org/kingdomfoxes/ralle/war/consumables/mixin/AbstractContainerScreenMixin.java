package org.kingdomfoxes.ralle.war.consumables.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional tail hook covering vanilla and compatible derived container screens. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "renderSlot", at = @At("TAIL"), require = 0)
    private void ralle$renderConsumableHighlight(
            GuiGraphics graphics,
            Slot slot,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        if (!RalleClient.initialized()) return;
        var highlights = RalleClient.context().consumableHighlights();
        highlights.style(slot.getItem()).ifPresent(style ->
                ConsumableSlotBorder.draw(graphics, slot.x, slot.y, style, highlights.timeMillis()));
    }
}
