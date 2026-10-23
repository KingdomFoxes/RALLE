package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive original-item evidence, before another mod can transform/cancel inventory display. */
@Mixin(value = ClientPacketListener.class, priority = 1200)
abstract class ClassInventorySignalsMixin {
    private static boolean requested() {
        return RalleClient.initialized() && RalleClient.context().classMenuScanner().requested()
                && Minecraft.getInstance().isSameThread();
    }
    @Inject(method = "handleContainerContent", at = @At("HEAD"), require = 1)
    private void ralle$classInventoryContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (requested()) RalleClient.context().classSwitcher().inventoryContents(packet.containerId(), packet.items());
    }
    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"), require = 1)
    private void ralle$classInventorySlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (requested()) RalleClient.context().classSwitcher().inventorySlot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }
    @Inject(method = "handleSetPlayerInventory", at = @At("HEAD"), require = 1)
    private void ralle$classInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if (requested() && packet.slot() == 7) RalleClient.context().classSwitcher().compassUpdated(packet.contents());
    }
}
