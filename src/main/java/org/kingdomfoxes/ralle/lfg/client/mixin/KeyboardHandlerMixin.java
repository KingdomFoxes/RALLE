package org.kingdomfoxes.ralle.lfg.client.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$consumeLfgChordDigit(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (RalleClient.initialized() && RalleClient.context().raidLfgKeybinds().handleKeyboard(event, action)) {
            callback.cancel();
        }
    }
}
