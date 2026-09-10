package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class ChatInputMenuClickMixin {
    // Observe after cancellable HEAD hooks, before vanilla changes the clicked item.
    @Inject(method = "handleInventoryMouseClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
    private void ralle$observeInputAction(int containerId, int slotId, int button, ClickType type,
                                          Player player, CallbackInfo ci) {
        RalleClient.context().chatInput().menuAction(containerId, slotId, button, type);
    }
}
