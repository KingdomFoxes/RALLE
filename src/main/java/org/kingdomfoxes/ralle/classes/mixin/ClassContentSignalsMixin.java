package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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
        RalleClient.context().classSwitcher().menuUpdated();
    }
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"), require = 1)
    private void ralle$classSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (!requested()) return;
        RalleClient.context().classSwitcher().menuUpdated();
    }
}
