package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.network.chat.Style;
import org.kingdomfoxes.ralle.chat.BatchedFullShadowSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphics$RenderingTextCollector")
abstract class RenderingTextCollectorMixin {
    @Redirect(
            method = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ActiveTextCollector;findElementUnderCursor(Lnet/minecraft/client/gui/render/state/GuiTextRenderState;FFLjava/util/function/Consumer;)V"
            )
    )
    private void ralle$skipVisualFullShadowHitTesting(
            GuiTextRenderState text,
            float mouseX,
            float mouseY,
            Consumer<Style> consumer
    ) {
        if (!(text.text instanceof BatchedFullShadowSequence)) {
            ActiveTextCollector.findElementUnderCursor(text, mouseX, mouseY, consumer);
        }
    }
}
