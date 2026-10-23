package org.kingdomfoxes.ralle.classes.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.kingdomfoxes.ralle.RalleClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 1100)
abstract class ClassSwitchMouseMixin {
    @Shadow private boolean isLeftPressed;
    @Shadow private boolean isMiddlePressed;
    @Shadow private boolean isRightPressed;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private MouseButtonInfo activeButton;
    @Shadow private double mousePressedTime;

    @Unique private static boolean ralle$switching() {
        return RalleClient.initialized() && RalleClient.context().classSwitcher().active();
    }
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void ralle$classClick(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (ralle$switching()) {
            isLeftPressed = isMiddlePressed = isRightPressed = false;
            activeButton = null; mousePressedTime = 0;
            ci.cancel();
        }
    }
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void ralle$classScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ralle$switching()) ci.cancel();
    }
    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), require = 1)
    private void ralle$classMotion(CallbackInfo ci) {
        if (ralle$switching()) { accumulatedDX = accumulatedDY = 0; activeButton = null; mousePressedTime = 0; }
    }
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void ralle$classCamera(double elapsed, CallbackInfo ci) { if (ralle$switching()) ci.cancel(); }
}
