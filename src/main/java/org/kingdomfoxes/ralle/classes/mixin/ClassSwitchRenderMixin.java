package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Composite after the complete live frame, preserving every vanilla world/GUI/network tick. */
@Mixin(Minecraft.class)
abstract class ClassSwitchRenderMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"), require = 1)
    private void ralle$classFrameStarting(boolean tick, CallbackInfo ci) {
        if (RalleClient.initialized()) RalleClient.context().classSwitcher().beforeRender();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"), require = 1)
    private void ralle$classFrame(boolean tick, CallbackInfo ci) {
        if (RalleClient.initialized()) RalleClient.context().classSwitcher().beforePresent();
    }
}
