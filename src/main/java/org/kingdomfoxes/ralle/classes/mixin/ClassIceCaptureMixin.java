package org.kingdomfoxes.ralle.classes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.ui.owo.ClassSelectorWheelScreen;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
abstract class ClassIceCaptureMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"), require = 1)
    private void ralle$background(GameRenderer renderer, Operation<Void> original) {
        if (GuiCaptureTargetOverride.current() == null && RalleClient.initialized())
            RalleClient.context().classSwitcher().captureBackground();
        // Class switching needs the HUD/foreground split without blurring gameplay.
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof ClassSelectorWheelScreen))
            original.call(renderer);
    }
}
