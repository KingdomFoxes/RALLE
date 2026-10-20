package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class ClassIceCaptureMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"), require = 1)
    private void ralle$background(GpuBufferSlice fog, CallbackInfo ci) {
        if (GuiCaptureTargetOverride.current() == null && RalleClient.initialized())
            RalleClient.context().classSwitcher().captureBackground();
    }
}
