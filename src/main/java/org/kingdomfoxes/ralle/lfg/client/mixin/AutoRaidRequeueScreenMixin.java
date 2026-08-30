package org.kingdomfoxes.ralle.lfg.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only server menus expected by an explicitly started automatic requeue. */
@Mixin(Minecraft.class)
public abstract class AutoRaidRequeueScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void ralle$suppressAutomaticRequeueMenu(Screen screen, CallbackInfo callback) {
        if (RalleClient.initialized() && RalleClient.context().autoRaidRequeue().suppressScreen(screen)) {
            callback.cancel();
        }
    }
}
