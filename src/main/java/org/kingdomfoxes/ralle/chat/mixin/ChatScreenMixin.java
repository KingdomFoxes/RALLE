package org.kingdomfoxes.ralle.chat.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.chat.input.ChatTypeTabService;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotSource;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;
    @Shadow private int historyPos;
    @Shadow protected boolean isDraft;
    @Unique private String ralle$lastInsertedChatTypePrefix;
    @Unique private String ralle$historyDraftPrefix;
    @Unique private long ralle$observedInputRevision;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void ralle$restoreChatType(CallbackInfo callback) {
        if (!ralle$channelEnabled()) return;
        boolean restoreInputDraft = this.isDraft && RalleClient.context().chatTypeTabs().inputSelectedAll();
        if (this.ralle$lastInsertedChatTypePrefix == null) {
            this.ralle$lastInsertedChatTypePrefix = RalleClient.context().chatTypeTabs().prefixForNewChat().orElse("");
        }
        ralle$adoptExplicitPrefix();
        if (restoreInputDraft) this.ralle$lastInsertedChatTypePrefix = "";
        this.ralle$observedInputRevision = RalleClient.context().chatTypeTabs().inputRequestRevision();
        ralle$layoutChannel();
    }

    @Inject(method = "onEdited", at = @At("TAIL"), require = 0)
    private void ralle$editChannel(String value, CallbackInfo callback) {
        if (!ralle$channelEnabled()) return;
        ralle$adoptExplicitPrefix();
        ralle$layoutChannel();
    }

    @Inject(method = "moveInHistory", at = @At("HEAD"), require = 0)
    private void ralle$historyChannel(int direction, CallbackInfo callback) {
        if (!ralle$channelEnabled()) return;
        int size = this.minecraft.gui.getChat().getRecentChat().size();
        int next = Math.clamp(this.historyPos + direction, 0, size);
        if (next == this.historyPos) return;
        if (this.historyPos == size) this.ralle$historyDraftPrefix = this.ralle$lastInsertedChatTypePrefix;
        this.ralle$lastInsertedChatTypePrefix = next == size ? this.ralle$historyDraftPrefix : "";
    }

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String ralle$routeChannel(String body) {
        ralle$syncInputRequest();
        return ralle$channelEnabled()
                ? ChatTypeTabService.outgoingMessage(body, this.ralle$lastInsertedChatTypePrefix) : body;
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void ralle$syncInputChannel(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        ralle$syncInputRequest();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void ralle$renderChannel(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (!ralle$channelEnabled() || this.input.getValue().startsWith("/")) return;
        graphics.drawString(this.font, ChatTypeTabService.channelLabel(this.ralle$lastInsertedChatTypePrefix),
                4, this.input.getY(), ChatTypeTabService.channelColor(this.ralle$lastInsertedChatTypePrefix));
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
    private void ralle$handleChatKeys(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        ralle$syncInputRequest();
        if (event.key() == GLFW.GLFW_KEY_TAB
                && event.modifiers() == 0
                && RalleClient.context().settings()
                        .setting(ChatTypeTabService.SETTING_ID, BooleanSetting.class)
                        .value()) {
            RalleClient.context().chatTypeTabs()
                    .nextPrefix(this.input.getValue().isEmpty() ? this.ralle$lastInsertedChatTypePrefix : this.input.getValue(),
                            this.input.getValue().isEmpty() ? this.ralle$lastInsertedChatTypePrefix : null)
                    .ifPresent(prefix -> {
                        this.ralle$setChatTypePrefix(prefix);
                        RalleClient.context().chatTypeTabs().rememberPrefix(prefix);
                        callback.setReturnValue(true);
                    });
            if (callback.isCancelled()) return;
        }
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
        ralle$syncInputRequest();
        RalleClient.context().chatScreenshots().cancel();
        if (ralle$channelEnabled()) {
            // Vanilla owns draft persistence; retain its destination with the body.
            this.input.setResponder(value -> {});
            this.input.setValue(ChatTypeTabService.outgoingMessage(this.input.getValue(), this.ralle$lastInsertedChatTypePrefix));
        }
    }

    private static boolean ralle$controlDown() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Unique
    private void ralle$syncInputRequest() {
        if (!ralle$channelEnabled() || this.input == null) return;
        var tabs = RalleClient.context().chatTypeTabs();
        if (this.ralle$observedInputRevision == tabs.inputRequestRevision()) return;
        this.ralle$observedInputRevision = tabs.inputRequestRevision();
        if (tabs.inputSelectedAll()) {
            this.ralle$historyDraftPrefix = "";
            ralle$setChatTypePrefix("");
        }
    }

    @Unique
    private void ralle$setChatTypePrefix(String prefix) {
        this.ralle$lastInsertedChatTypePrefix = prefix;
        ralle$layoutChannel();
    }

    @Unique
    private boolean ralle$channelEnabled() {
        return RalleClient.context().settings().setting(ChatTypeTabService.SETTING_ID, BooleanSetting.class).value();
    }

    @Unique
    private void ralle$adoptExplicitPrefix() {
        String value = this.input.getValue();
        ChatTypeTabService.explicitPrefix(value).ifPresent(prefix -> {
            this.ralle$lastInsertedChatTypePrefix = prefix;
            this.input.setValue(value.substring(prefix.length()));
        });
    }

    @Unique
    private void ralle$layoutChannel() {
        boolean command = this.input.getValue().startsWith("/");
        String label = ChatTypeTabService.channelLabel(this.ralle$lastInsertedChatTypePrefix);
        int left = command ? 4 : 4 + this.font.width(label) + 5;
        this.input.setX(left);
        this.input.setWidth(Math.max(1, this.width - left));
        this.input.setMessage(command ? Component.translatable("chat.editBox")
                : Component.literal(label + " ").append(Component.translatable("chat.editBox")));
    }
}
