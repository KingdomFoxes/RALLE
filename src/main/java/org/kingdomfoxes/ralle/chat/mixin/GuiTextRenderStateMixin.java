package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.kingdomfoxes.ralle.chat.BatchedFullShadowSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiTextRenderState.class)
abstract class GuiTextRenderStateMixin {
    @Shadow @Final public Font font;
    @Shadow @Final public FormattedCharSequence text;
    @Shadow @Final public Matrix3x2fc pose;
    @Shadow @Final public int x;
    @Shadow @Final public int y;
    @Shadow @Final public int color;
    @Shadow @Final @Nullable public ScreenRectangle scissor;
    @Shadow private Font.PreparedText preparedText;
    @Shadow @Nullable private ScreenRectangle bounds;

    @Inject(method = "ensurePrepared", at = @At("HEAD"), cancellable = true)
    private void ralle$prepareBatchedFullShadow(CallbackInfoReturnable<Font.PreparedText> callback) {
        if (!(text instanceof BatchedFullShadowSequence shadow)) return;

        if (preparedText == null) {
            preparedText = shadow.prepare(font, x, y, color);
            var preparedBounds = preparedText.bounds();
            if (preparedBounds != null) {
                preparedBounds = preparedBounds.transformMaxBounds(pose);
                bounds = scissor == null ? preparedBounds : scissor.intersection(preparedBounds);
            }
        }

        callback.setReturnValue(preparedText);
    }
}
