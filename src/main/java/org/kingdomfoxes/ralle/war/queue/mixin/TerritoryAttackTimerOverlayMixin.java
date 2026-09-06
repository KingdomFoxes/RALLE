package org.kingdomfoxes.ralle.war.queue.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.wynntils.models.territories.TerritoryAttackTimer;
import com.wynntils.utils.render.TextRenderTask;
import org.kingdomfoxes.ralle.war.queue.QueueAttributionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.wynntils.overlays.TerritoryAttackTimerOverlay", remap = false)
abstract class TerritoryAttackTimerOverlayMixin {
    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/models/territories/GuildAttackTimerModel;getAttackTimers()Ljava/util/List;",
                    remap = false
            ),
            require = 0
    )
    private List<TerritoryAttackTimer> ralle$addDevelopmentTimers(List<TerritoryAttackTimer> original) {
        var demo = QueueAttributionService.developmentTimers();
        if (demo.isEmpty()) return original;
        var combined = new ArrayList<>(original);
        demo.forEach(timer -> combined.add(new TerritoryAttackTimer(timer.key(), timer.timerEndMillis())));
        return List.copyOf(combined);
    }

    @Inject(
            method = "lambda$render$0(Lcom/wynntils/models/territories/TerritoryAttackTimer;)Lcom/wynntils/utils/render/TextRenderTask;",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void ralle$attributeTimer(
            TerritoryAttackTimer timer,
            CallbackInfoReturnable<TextRenderTask> callback
    ) {
        QueueAttributionService.decorateTimer(timer, callback.getReturnValue());
    }

    @ModifyArg(
            method = "renderPreview",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/utils/render/FontRenderer;renderTextWithAlignment(Lnet/minecraft/client/gui/GuiGraphics;FFLcom/wynntils/utils/render/TextRenderTask;FFLcom/wynntils/utils/render/type/HorizontalAlignment;Lcom/wynntils/utils/render/type/VerticalAlignment;)V",
                    remap = false
            ),
            index = 3,
            require = 0,
            remap = false
    )
    private TextRenderTask ralle$attributePreview(TextRenderTask task) {
        return (TextRenderTask) QueueAttributionService.decoratePreview(task);
    }
}
