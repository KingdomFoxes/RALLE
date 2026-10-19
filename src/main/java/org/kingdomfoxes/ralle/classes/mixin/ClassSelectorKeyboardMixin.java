package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Class input has one owner before screen/gameplay callbacks; ordinary input passes through. */
@Mixin(value = KeyboardHandler.class, priority = 1100)
abstract class ClassSelectorKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 1)
    private void ralle$classInput(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (RalleClient.initialized() && window == Minecraft.getInstance().getWindow().handle()
                && RalleClient.context().classSelectorBindings().handleKeyboard(event, action)) callback.cancel();
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 1)
    private void ralle$classTyping(long window, CharacterEvent event, CallbackInfo callback) {
        if (RalleClient.initialized() && RalleClient.context().classSwitcher().active()) callback.cancel();
    }
}
