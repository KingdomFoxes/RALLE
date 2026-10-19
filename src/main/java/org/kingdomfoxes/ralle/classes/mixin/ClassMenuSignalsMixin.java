package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe original text even when another mod transforms or suppresses its display. */
@Mixin(value = ClientPacketListener.class, priority = 1200)
abstract class ClassMenuSignalsMixin {
    @Inject(method = "handleOpenScreen", at = @At("HEAD"), require = 1)
    private void ralle$classMenuOpened(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (RalleClient.initialized() && RalleClient.context().classMenuScanner().requested() && Minecraft.getInstance().isSameThread()) {
            RalleClient.context().classMenuScanner().opened(packet.getContainerId(), packet.getTitle().getString());
            RalleClient.context().classSwitcher().opened(packet.getContainerId(), packet.getTitle().getString());
        }
    }

    @Inject(method = "setActionBarText", at = @At("HEAD"), require = 1)
    private void ralle$classStage(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (RalleClient.initialized() && RalleClient.context().classMenuCapture().requested() && Minecraft.getInstance().isSameThread()) {
            RalleClient.context().classMenuCapture().actionBar(packet.text().getString());
            RalleClient.context().classSwitcher().actionBar(packet.text().getString());
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), require = 1)
    private void ralle$classOverlay(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet.overlay() && RalleClient.initialized() && RalleClient.context().classMenuCapture().requested() && Minecraft.getInstance().isSameThread()) {
            RalleClient.context().classMenuCapture().actionBar(packet.content().getString());
            RalleClient.context().classSwitcher().actionBar(packet.content().getString());
        }
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"), require = 1)
    private void ralle$classMenuClosed(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        if (RalleClient.initialized() && Minecraft.getInstance().isSameThread())
            RalleClient.context().classSwitcher().closed(packet.getContainerId());
    }
}
