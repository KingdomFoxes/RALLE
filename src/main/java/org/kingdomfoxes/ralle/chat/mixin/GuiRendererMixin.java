package org.kingdomfoxes.ralle.chat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"),
            require = 1
    )
    private RenderTarget ralle$useCaptureTarget(Minecraft minecraft) {
        RenderTarget override = GuiCaptureTargetOverride.current();
        return override == null ? minecraft.getMainRenderTarget() : override;
    }
}
