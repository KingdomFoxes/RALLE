package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ChatInputServerMenuMixin {
    // Fabric ALLOW_GAME is too late when Wynntils cancels the enclosing ChatListener call.
    // This invocation is after vanilla's thread handoff and before display transformations.
    @Inject(method = "handleSystemChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void ralle$originalInputPrompt(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        RalleClient.context().chatInput().observePrompt(packet.content());
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void ralle$serverAcknowledgedInput(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            RalleClient.context().chatInput().serverClosed(packet.getContainerId());
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"))
    private void ralle$replacedMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) RalleClient.context().chatInput().reset();
    }
}
