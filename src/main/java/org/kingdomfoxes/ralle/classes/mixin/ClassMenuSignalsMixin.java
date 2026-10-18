package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe original text even when another mod transforms or suppresses its display. */
@Mixin(ClientPacketListener.class)
abstract class ClassMenuSignalsMixin {
    @Inject(method = "handleOpenScreen", at = @At("TAIL"), require = 1)
    private void ralle$classMenuOpened(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (RalleClient.initialized() && RalleClient.context().classMenuScanner().requested() && Minecraft.getInstance().isSameThread())
            RalleClient.context().classMenuScanner().opened(packet.getContainerId(), packet.getTitle().getString());
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"), require = 1)
    private void ralle$classStage(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (RalleClient.initialized() && RalleClient.context().classMenuCapture().requested() && Minecraft.getInstance().isSameThread())
            RalleClient.context().classMenuCapture().actionBar(packet.text().getString());
    }

    @Inject(method = "handleSystemChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void ralle$classOverlay(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (packet.overlay() && RalleClient.initialized() && RalleClient.context().classMenuCapture().requested() && Minecraft.getInstance().isSameThread())
            RalleClient.context().classMenuCapture().actionBar(packet.content().getString());
    }
}
