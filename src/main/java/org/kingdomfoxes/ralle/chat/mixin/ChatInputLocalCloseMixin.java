package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class ChatInputLocalCloseMixin {
    @Inject(method = "closeContainer", at = @At("HEAD"))
    private void ralle$manualCloseIsNotInput(CallbackInfo ci) {
        RalleClient.context().chatInput().reset();
    }
}
