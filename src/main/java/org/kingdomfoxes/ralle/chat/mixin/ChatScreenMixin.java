package org.kingdomfoxes.ralle.chat.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotSource;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$selectChatMessages(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callback) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        var service = RalleClient.context().chatScreenshots();
        if (ralle$controlDown()) {
            boolean began = service.begin(
                    (ChatScreenshotSource) net.minecraft.client.Minecraft.getInstance().gui.getChat(),
                    event.x(),
                    event.y()
            );
            if (began) callback.setReturnValue(true);
            return;
        }
        if (doubleClick && service.previewContains(event.x(), event.y())) {
            service.copyPreview();
            callback.setReturnValue(true);
        } else if (service.state() == ChatScreenshotService.State.PREVIEW) {
            boolean inside = service.previewContains(event.x(), event.y());
            if (!inside) service.cancel();
            else callback.setReturnValue(true);
        } else if (service.active()) {
            service.cancel();
        }
    }

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$handleLocalInviteAction(Style style, boolean insertionClickMode,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (!insertionClickMode
                && RalleClient.context().hostPartyInvites().handleClick(style.getClickEvent())) {
            callback.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        var service = RalleClient.context().chatScreenshots();
        if (service.state() == ChatScreenshotService.State.DRAGGING) {
            service.drag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        var service = RalleClient.context().chatScreenshots();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && service.state() == ChatScreenshotService.State.DRAGGING) {
            service.releaseLeft();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$handleScreenshotKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        var service = RalleClient.context().chatScreenshots();
        if (event.key() == GLFW.GLFW_KEY_C && ralle$controlDown() && service.copyPreview()) {
            callback.setReturnValue(true);
            return;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && service.active()) {
            service.cancel();
            callback.setReturnValue(true);
            return;
        }
        if ((event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) && service.active()) {
            service.cancelForManualScroll();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT_CONTROL || event.key() == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            RalleClient.context().chatScreenshots().releaseControl(ralle$controlDown());
        }
        return super.keyReleased(event);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void ralle$cancelScreenshotForScroll(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> callback) {
        RalleClient.context().chatScreenshots().cancelForManualScroll();
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void ralle$cancelScreenshotOnClose(CallbackInfo callback) {
        RalleClient.context().chatScreenshots().cancel();
    }

    private static boolean ralle$controlDown() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
