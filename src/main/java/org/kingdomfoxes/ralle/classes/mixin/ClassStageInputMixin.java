package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Queue the one approved click immediately before vanilla consumes Use, after normal tick updates. */
@Mixin(Minecraft.class)
abstract class ClassStageInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"), require = 1)
    private void ralle$classUse(CallbackInfo ci) {
        if (RalleClient.initialized()) RalleClient.context().classSwitcher().beforeInput();
    }
}
