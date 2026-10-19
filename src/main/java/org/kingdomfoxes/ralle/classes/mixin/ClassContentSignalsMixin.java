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

/** Vanilla has applied these contents: target selection need not wait for another client tick. */
@Mixin(ClientPacketListener.class)
abstract class ClassContentSignalsMixin {
    private static boolean requested() {
        return RalleClient.initialized() && RalleClient.context().classMenuScanner().requested()
                && Minecraft.getInstance().isSameThread();
    }
    @Inject(method = "handleContainerContent", at = @At("TAIL"), require = 1)
    private void ralle$classContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!requested()) return;
        var switcher = RalleClient.context().classSwitcher();
        switcher.menuUpdated();
        // Player inventory hotbar index 7 is container slot 36 + 7.
        if (packet.containerId() == 0 && packet.items().size() > 43) switcher.compassUpdated(packet.items().get(43));
    }
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"), require = 1)
    private void ralle$classSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (!requested()) return;
        var switcher = RalleClient.context().classSwitcher();
        switcher.menuUpdated();
        if (packet.getContainerId() == 0 && packet.getSlot() == 43) switcher.compassUpdated(packet.getItem());
    }
    @Inject(method = "handleSetPlayerInventory", at = @At("TAIL"), require = 1)
    private void ralle$classInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if (requested() && packet.slot() == 7) RalleClient.context().classSwitcher().compassUpdated(packet.contents());
    }
}
