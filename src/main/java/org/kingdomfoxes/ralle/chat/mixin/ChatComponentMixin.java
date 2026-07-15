package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatGraphicsTransform;
import org.kingdomfoxes.ralle.chat.ChatMessageProjector;
import org.kingdomfoxes.ralle.chat.ChatRenderLayout;
import org.kingdomfoxes.ralle.chat.render.FullShadowFrameCollector;
import org.kingdomfoxes.ralle.chat.render.FullShadowRenderingStrategy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    private boolean ralle$refreshingMessages;
    @Unique private boolean ralle$capturingClickableText;
    @Unique private FullShadowFrameCollector ralle$fullShadowCollector;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void ralle$startFullShadowFrame(
            GuiGraphics graphics,
            Font font,
            int guiTick,
            int mouseX,
            int mouseY,
            boolean focused,
            boolean changeCursorOnInsertions,
            CallbackInfo callback
    ) {
        ralle$fullShadowCollector = null;
        var behavior = RalleClient.context().chatBehavior();
        if (behavior.textShadow() != ChatBehaviorService.TextShadow.FULL
                || !FullShadowRenderingStrategy.compositorAvailable()) {
            return;
        }

        double scale = Math.max(0.01, minecraft.options.chatScale().get());
        int canvasHeight = RalleClient.context().chatLayout().activeCustomBounds()
                .map(RalleClient.context().chatLayout()::customRenderCanvasHeight)
                .orElse(graphics.guiHeight());
        int localBottom = Mth.floor((canvasHeight - 40) / scale);
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1.0));
        int localHeight = Math.max(1, ((ChatComponent) (Object) this).getLinesPerPage() * lineHeight);
        ralle$fullShadowCollector = new FullShadowFrameCollector(
                graphics,
                font,
                ralle$renderContentWidth(),
                localBottom - localHeight,
                localBottom
        );
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("RETURN"),
            require = 0
    )
    private void ralle$finishFullShadowFrame(
            GuiGraphics graphics,
            Font font,
            int guiTick,
            int mouseX,
            int mouseY,
            boolean focused,
            boolean changeCursorOnInsertions,
            CallbackInfo callback
    ) {
        ralle$fullShadowCollector = null;
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"), require = 0)
    private void ralle$startClickableTextCapture(
            ActiveTextCollector output,
            int canvasHeight,
            int guiTick,
            boolean focused,
            CallbackInfo callback
    ) {
        ralle$capturingClickableText = true;
    }

    @Inject(method = "captureClickableText", at = @At("TAIL"), require = 0)
    private void ralle$finishClickableTextCapture(
            ActiveTextCollector output,
            int canvasHeight,
            int guiTick,
            boolean focused,
            CallbackInfo callback
    ) {
        ralle$capturingClickableText = false;
    }

    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true, require = 0)
    private void ralle$useCustomWidth(CallbackInfoReturnable<Integer> callback) {
        RalleClient.context().chatLayout().activeCustomBounds()
                .ifPresent(bounds -> callback.setReturnValue(bounds.width()));
    }

    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true, require = 0)
    private void ralle$useCustomHeight(CallbackInfoReturnable<Integer> callback) {
        var chatLayout = RalleClient.context().chatLayout();
        chatLayout.activeCustomBounds()
                .ifPresent(bounds -> callback.setReturnValue(chatLayout.customUnscaledHeight(bounds)));
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0
    )
    private int ralle$useCustomBottomAnchor(int vanillaCanvasHeight) {
        var chatLayout = RalleClient.context().chatLayout();
        return chatLayout.activeCustomBounds()
                .map(chatLayout::customRenderCanvasHeight)
                .orElse(vanillaCanvasHeight);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void ralle$useCustomHorizontalAnchor(
            ChatComponent.ChatGraphicsAccess graphics,
            int canvasHeight,
            int guiTick,
            boolean focused,
            CallbackInfo callback
    ) {
        RalleClient.context().chatLayout().activeCustomBounds()
                .ifPresent(bounds -> graphics.updatePose(matrix -> matrix.translate(bounds.x(), 0)));
    }

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0
    )
    private ChatComponent.ChatGraphicsAccess ralle$transformChatGraphics(ChatComponent.ChatGraphicsAccess graphics) {
        var behavior = RalleClient.context().chatBehavior();
        return ChatGraphicsTransform.wrap(
                graphics,
                minecraft.font,
                ralle$renderContentWidth(),
                behavior.horizontalAlignment(),
                behavior.textShadow(),
                !ralle$capturingClickableText,
                ralle$capturingClickableText ? null : ralle$fullShadowCollector
        );
    }

    @ModifyArg(
            method = "forEachLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/GuiMessage$Line;IF)V"
            ),
            index = 1,
            require = 0
    )
    private int ralle$applyMessageDirection(int lineIndex) {
        return ChatRenderLayout.lineIndex(
                RalleClient.context().chatBehavior().messageDirection(),
                lineIndex,
                ((ChatComponent) (Object) this).getLinesPerPage()
        );
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), require = 0)
    private void ralle$startMessageRefresh(CallbackInfo callback) {
        ralle$refreshingMessages = true;
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("TAIL"), require = 0)
    private void ralle$finishMessageRefresh(CallbackInfo callback) {
        if (RalleClient.context().chatBehavior().projectionEnabled()) {
            ralle$refreshProjectedMessages();
        }
        ralle$refreshingMessages = false;
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$deferProjectedMessageDisplay(GuiMessage message, CallbackInfo callback) {
        if (!RalleClient.context().chatBehavior().projectionEnabled()) return;

        if (!ralle$refreshingMessages
                && ((ChatComponent) (Object) this).isChatFocused()
                && chatScrollbarPos > 0) {
            int addedLineCount = message.splitLines(minecraft.font, ralle$contentWidth()).size();
            for (int line = 0; line < addedLineCount; line++) {
                newMessageSinceScroll = true;
                ((ChatComponent) (Object) this).scrollChat(1);
            }
        }
        callback.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void ralle$refreshAfterMessageAdded(CallbackInfo callback) {
        if (RalleClient.context().chatBehavior().projectionEnabled()) {
            ralle$refreshProjectedMessages();
        }
    }

    private void ralle$refreshProjectedMessages() {
        var behavior = RalleClient.context().chatBehavior();
        var projected = ChatMessageProjector.project(
                allMessages,
                behavior.compactChatEnabled(),
                behavior.stackEmptyLinesEnabled(),
                ChatBehaviorService.DEFAULT_COMPACT_WINDOW_TICKS
        );

        trimmedMessages.clear();
        int contentWidth = ralle$contentWidth();
        for (int messageIndex = projected.size() - 1; messageIndex >= 0; messageIndex--) {
            var message = projected.get(messageIndex);
            var displayMessage = new GuiMessage(message.addedTime(), message.content(), null, message.tag());
            var lines = displayMessage.splitLines(minecraft.font, contentWidth);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                trimmedMessages.addFirst(new GuiMessage.Line(
                        message.addedTime(),
                        lines.get(lineIndex),
                        message.tag(),
                        lineIndex == lines.size() - 1
                ));
            }
        }

        while (trimmedMessages.size() > 100) {
            trimmedMessages.removeLast();
        }
    }

    private int ralle$contentWidth() {
        double scale = Math.max(0.01, minecraft.options.chatScale().get());
        return Math.max(1, Mth.floor(ralle$visualWidth() / scale));
    }

    private int ralle$renderContentWidth() {
        double scale = Math.max(0.01, minecraft.options.chatScale().get());
        return Math.max(1, Mth.ceil(ralle$visualWidth() / scale));
    }

    private int ralle$visualWidth() {
        return RalleClient.context().chatLayout().activeCustomBounds()
                .map(bounds -> bounds.width())
                .orElseGet(() -> ChatComponent.getWidth(minecraft.options.chatWidth().get()));
    }
}
