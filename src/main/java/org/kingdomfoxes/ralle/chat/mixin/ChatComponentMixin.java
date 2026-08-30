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
import org.kingdomfoxes.ralle.chat.ChatHistoryRetention;
import org.kingdomfoxes.ralle.chat.ChatMessageProjector;
import org.kingdomfoxes.ralle.chat.ChatRenderLayout;
import org.kingdomfoxes.ralle.chat.ChatScrollbarGraphics;
import org.kingdomfoxes.ralle.chat.ChatSystemIndicators;
import org.kingdomfoxes.ralle.chat.ChatTimestampStore;
import org.kingdomfoxes.ralle.chat.ChatTimestamps;
import org.kingdomfoxes.ralle.chat.TemporaryGuildRankOverride;
import org.kingdomfoxes.ralle.chat.render.FullShadowFrameCollector;
import org.kingdomfoxes.ralle.chat.render.FullShadowRenderingStrategy;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotSnapshot;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin implements ChatScreenshotSource {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    private boolean ralle$refreshingMessages;
    @Unique private boolean ralle$capturingClickableText;
    @Unique private FullShadowFrameCollector ralle$fullShadowCollector;
    @Unique private List<GuiMessage> ralle$transitionMessages;
    @Unique private final ChatTimestampStore ralle$timestampStore = new ChatTimestampStore(Clock.systemDefaultZone());

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
        RalleClient.context().chatScreenshots().renderOutline(graphics);
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
        int contentWidth = ralle$renderContentWidth();
        var transformed = ChatGraphicsTransform.wrap(
                graphics,
                minecraft.font,
                contentWidth,
                behavior.horizontalAlignment(),
                behavior.textShadow(),
                !ralle$capturingClickableText,
                ralle$capturingClickableText ? null : ralle$fullShadowCollector
        );
        return ChatScrollbarGraphics.wrap(transformed, contentWidth, behavior.hideChatScrollbar());
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I",
                    ordinal = 1
            ),
            require = 0
    )
    private void ralle$drawScreenshotFillBehindText(
            ChatComponent.ChatGraphicsAccess graphics,
            int canvasHeight,
            int guiTick,
            boolean focused,
            CallbackInfo callback
    ) {
        RalleClient.context().chatScreenshots().renderLocalFill(graphics);
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
        allMessages.forEach(ralle$timestampStore::record);
        ralle$pruneHistory();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("TAIL"), require = 0)
    private void ralle$finishMessageRefresh(CallbackInfo callback) {
        if (RalleClient.context().chatBehavior().projectionEnabled()) {
            ralle$refreshProjectedMessages();
        }
        ralle$refreshingMessages = false;
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), require = 0)
    private void ralle$preserveTransitionMessages(boolean clearRecentChat, CallbackInfo callback) {
        var behavior = RalleClient.context().chatBehavior();
        if (clearRecentChat && behavior.persistentChatEnabled()) {
            ralle$transitionMessages = new ArrayList<>(allMessages);
        } else {
            ralle$transitionMessages = null;
            ralle$timestampStore.clear();
        }
    }

    @Inject(method = "clearMessages", at = @At("TAIL"), require = 0)
    private void ralle$restoreTransitionMessages(boolean clearRecentChat, CallbackInfo callback) {
        if (ralle$transitionMessages == null) return;

        allMessages.addAll(ralle$transitionMessages);
        ralle$transitionMessages = null;
        ralle$pruneHistory();
        ((ChatComponent) (Object) this).rescaleChat();
    }

    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100), require = 0)
    private int ralle$logicalMessageLimit(int vanillaLimit) {
        return RalleClient.context().chatBehavior().effectiveHistoryLimit();
    }

    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100), require = 0)
    private int ralle$wrappedLineLimit(int vanillaLimit) {
        return RalleClient.context().chatBehavior().effectiveHistoryLimit();
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
    private void ralle$deferProjectedMessageDisplay(GuiMessage message, CallbackInfo callback) {
        if (!ralle$refreshingMessages) ralle$timestampStore.record(message);
        message = ChatSystemIndicators.withoutIndicator(
                message,
                RalleClient.context().chatBehavior().removeChatSystemIndicators()
        );
        if (RalleClient.context().chatScreenshots().stabilizesIncomingMessages()) {
            callback.cancel();
            return;
        }
        if (!RalleClient.context().chatBehavior().projectionEnabled()) return;

        if (!ralle$refreshingMessages && ((ChatComponent) (Object) this).isChatFocused()
                && (chatScrollbarPos > 0 || RalleClient.context().chatScreenshots().stabilizesIncomingMessages())) {
            int addedLineCount = message.splitLines(minecraft.font, ralle$contentWidth(message)).size();
            for (int line = 0; line < addedLineCount; line++) {
                newMessageSinceScroll = true;
                ((ChatComponent) (Object) this).scrollChat(1);
            }
        }
        callback.cancel();
    }

    @ModifyVariable(
            method = "addMessageToDisplayQueue",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private GuiMessage ralle$removeChatSystemIndicatorBeforeWrapping(GuiMessage message) {
        return ChatSystemIndicators.withoutIndicator(
                message,
                RalleClient.context().chatBehavior().removeChatSystemIndicators()
        );
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void ralle$refreshAfterMessageAdded(CallbackInfo callback) {
        if (RalleClient.context().chatBehavior().projectionEnabled()
                && !RalleClient.context().chatScreenshots().stabilizesIncomingMessages()) {
            ralle$refreshProjectedMessages();
        }
    }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"), require = 0)
    private void ralle$recordAndPruneTimestampAfterMessageAdded(GuiMessage message, CallbackInfo callback) {
        ralle$timestampStore.record(message);
        ralle$timestampStore.retainAll(allMessages);
    }

    @Inject(method = "createDeletedMarker", at = @At("RETURN"), require = 0)
    private void ralle$transferDeletedMessageTimestamp(
            GuiMessage original,
            CallbackInfoReturnable<GuiMessage> callback
    ) {
        ralle$timestampStore.transfer(original, callback.getReturnValue());
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0
    )
    private net.minecraft.network.chat.Component ralle$temporarilyReplaceStrategistRank(
            net.minecraft.network.chat.Component message
    ) {
        if (!RalleClient.initialized()
                || !RalleClient.context().chatBehavior().chatCustomizationActive()) return message;
        return TemporaryGuildRankOverride.apply(message);
    }

    private void ralle$refreshProjectedMessages() {
        var behavior = RalleClient.context().chatBehavior();
        var projected = ChatMessageProjector.project(
                allMessages,
                behavior.compactChatEnabled(),
                behavior.stackEmptyLinesEnabled(),
                ChatBehaviorService.DEFAULT_COMPACT_WINDOW_TICKS,
                ralle$timestampStore::receiveTime
        );

        trimmedMessages.clear();
        int contentWidth = ralle$contentWidth();
        for (int messageIndex = projected.size() - 1; messageIndex >= 0; messageIndex--) {
            var message = projected.get(messageIndex);
            var displayMessage = ChatSystemIndicators.withoutIndicator(
                    new GuiMessage(message.addedTime(), message.content(), null, message.tag()),
                    behavior.removeChatSystemIndicators()
            );
            var prefix = behavior.chatTimestampsEnabled() && message.receiveTime() != null
                    ? ChatTimestamps.prefix(message.receiveTime()).getVisualOrderText()
                    : null;
            int wrappedContentWidth = prefix == null
                    ? contentWidth
                    : Math.max(1, contentWidth - minecraft.font.width(prefix));
            var lines = displayMessage.splitLines(minecraft.font, wrappedContentWidth);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                trimmedMessages.addFirst(new GuiMessage.Line(
                        message.addedTime(),
                        prefix == null ? lines.get(lineIndex) : ChatTimestamps.prepend(prefix, lines.get(lineIndex)),
                        displayMessage.tag(),
                        lineIndex == lines.size() - 1
                ));
            }
        }

        ChatHistoryRetention.pruneOldest(trimmedMessages, behavior.effectiveHistoryLimit());
    }

    @Unique
    private void ralle$pruneHistory() {
        int limit = RalleClient.context().chatBehavior().effectiveHistoryLimit();
        ChatHistoryRetention.pruneOldest(allMessages, limit);
        ChatHistoryRetention.pruneOldest(trimmedMessages, limit);
        ralle$timestampStore.retainAll(allMessages);
        ((ChatComponent) (Object) this).scrollChat(0);
    }

    private int ralle$contentWidth() {
        double scale = Math.max(0.01, minecraft.options.chatScale().get());
        return Math.max(1, Mth.floor(ralle$visualWidth() / scale));
    }

    private int ralle$contentWidth(GuiMessage message) {
        int contentWidth = ralle$contentWidth();
        var behavior = RalleClient.context().chatBehavior();
        var receiveTime = ralle$timestampStore.receiveTime(message);
        if (!behavior.chatTimestampsEnabled() || receiveTime == null) return contentWidth;
        return Math.max(1, contentWidth - minecraft.font.width(ChatTimestamps.prefix(receiveTime)));
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

    @Override
    public ChatScreenshotSnapshot ralle$freezeScreenshotMessages() {
        var lines = new java.util.ArrayList<ChatScreenshotSnapshot.FrozenLine>(trimmedMessages.size());
        int messageIndex = -1;
        for (GuiMessage.Line line : trimmedMessages) {
            if (line.endOfEntry()) messageIndex++;
            if (messageIndex < 0) messageIndex = 0;
            lines.add(new ChatScreenshotSnapshot.FrozenLine(
                    line.content(),
                    messageIndex,
                    minecraft.font.width(line.content())
            ));
        }

        double scale = Math.max(0.01, minecraft.options.chatScale().get());
        int lineHeight = (int) (9 * (minecraft.options.chatLineSpacing().get() + 1.0));
        int baselineFromTop = lineHeight - (int) Math.round(
                8.0 * (minecraft.options.chatLineSpacing().get() + 1.0)
                        - 4.0 * minecraft.options.chatLineSpacing().get()
        );
        int linesPerPage = Math.max(1, ((ChatComponent) (Object) this).getLinesPerPage());
        var customBounds = RalleClient.context().chatLayout().activeCustomBounds();
        int viewportLeft = customBounds.map(bounds -> bounds.x()).orElse(0);
        int canvasHeight = customBounds
                .map(RalleClient.context().chatLayout()::customRenderCanvasHeight)
                .orElse(minecraft.getWindow().getGuiScaledHeight());
        int localBottom = Mth.floor((canvasHeight - 40) / scale);
        int viewportBottom = Mth.floor(localBottom * scale);
        int viewportTop = viewportBottom - Mth.ceil(linesPerPage * lineHeight * scale);
        int viewportRight = Math.min(
                minecraft.getWindow().getGuiScaledWidth(),
                viewportLeft + RalleClient.context().chatLayout().renderedChatWidth(ralle$visualWidth())
        );
        return new ChatScreenshotSnapshot(
                lines,
                chatScrollbarPos,
                linesPerPage,
                lineHeight,
                baselineFromTop,
                scale,
                minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F,
                ralle$renderContentWidth(),
                viewportLeft,
                viewportTop,
                viewportRight,
                viewportBottom,
                RalleClient.context().chatBehavior().messageDirection(),
                RalleClient.context().chatBehavior().horizontalAlignment(),
                RalleClient.context().chatBehavior().textShadow()
        );
    }

    @Override
    public int ralle$screenshotScroll() {
        return chatScrollbarPos;
    }

    @Override
    public void ralle$selectionAutoscroll(int amount) {
        ((ChatComponent) (Object) this).scrollChat(amount);
    }
}
