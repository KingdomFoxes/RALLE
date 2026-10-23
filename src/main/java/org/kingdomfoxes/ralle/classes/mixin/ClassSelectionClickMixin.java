package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe native clicks too, so a manual book selection can establish the current character. */
@Mixin(MultiPlayerGameMode.class)
abstract class ClassSelectionClickMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), require = 1)
    private void ralle$classSelection(int container, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (RalleClient.initialized()) RalleClient.context().classSwitcher().inventoryClick(container, slot, button, type);
    }
}
