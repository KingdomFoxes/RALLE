package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bounded packet-type evidence for native and manual clicks in the observed selection scene. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClassStagePacketMixin {
    @Inject(method = "send", at = @At("HEAD"), require = 1)
    private void ralle$classInteraction(Packet<?> packet, CallbackInfo ci) {
        if (RalleClient.initialized() && Minecraft.getInstance().isSameThread())
            RalleClient.context().classSwitcher().outgoing(packet);
    }
}
