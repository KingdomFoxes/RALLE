package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true, require = 0)
    private void ralle$useCustomWidth(CallbackInfoReturnable<Integer> callback) {
        RalleClient.context().chatLayout().activeCustomBounds()
                .ifPresent(bounds -> callback.setReturnValue(bounds.width()));
    }

    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true, require = 0)
    private void ralle$useCustomHeight(CallbackInfoReturnable<Integer> callback) {
        var chatLayout = RalleClient.context().chatLayout();
        chatLayout.activeCustomBounds()
                .ifPresent(bounds -> callback.setReturnValue(chatLayout.customUnscaledHeight(bounds)));
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0
    )
    private int ralle$useCustomBottomAnchor(int vanillaCanvasHeight) {
        var chatLayout = RalleClient.context().chatLayout();
        return chatLayout.activeCustomBounds()
                .map(chatLayout::customRenderCanvasHeight)
                .orElse(vanillaCanvasHeight);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void ralle$useCustomHorizontalAnchor(
            ChatComponent.ChatGraphicsAccess graphics,
            int canvasHeight,
            int guiTick,
            boolean focused,
            CallbackInfo callback
    ) {
        RalleClient.context().chatLayout().activeCustomBounds()
                .ifPresent(bounds -> graphics.updatePose(matrix -> matrix.translate(bounds.x(), 0)));
    }
}
