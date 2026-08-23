package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotGeometry.LineRange;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotGeometry.Rectangle;
import org.kingdomfoxes.ralle.sound.ChatSelectionSoundPlayer;
import org.kingdomfoxes.ralle.ui.owo.RalleTypography;

import java.util.ArrayList;
import java.util.OptionalInt;

public final class ChatScreenshotService {
    public enum State { IDLE, DRAGGING, PREVIEW, COPYING, COPIED_FADING }

    private final Minecraft minecraft;
    private final BooleanSetting screenshotEnabled;
    private final BooleanSetting smoothExpansion;
    private final ChatScreenshotCapture capture;
    private final ChatSelectionSoundFeedback soundFeedback;

    private final ChatScreenshotLifecycle lifecycle = new ChatScreenshotLifecycle();
    private ChatScreenshotSource source;
    private ChatScreenshotSnapshot snapshot;
    private int snapshotScroll;
    private int anchorMessage;
    private int currentMessage;
    private boolean controlHeld;
    private boolean leftHeld;
    private double pointerX;
    private double pointerY;
    private Rectangle animationFrom;
    private Rectangle targetBounds;
    private long animationStarted;
    private final ChatSelectionAutoscroll autoscroll = new ChatSelectionAutoscroll();
    private long copyGeneration;

    public ChatScreenshotService(
            Minecraft minecraft,
            SettingsRegistry settings,
            ChatScreenshotCapture capture,
            ChatSelectionSoundPlayer soundPlayer
    ) {
        this.minecraft = minecraft;
        this.screenshotEnabled = settings.setting("chat-screenshot-enabled", BooleanSetting.class);
        this.smoothExpansion = settings.setting("chat-screenshot-smooth-expansion", BooleanSetting.class);
        this.capture = capture;
        this.soundFeedback = new ChatSelectionSoundFeedback(soundPlayer);
    }

    public boolean enabled() {
        return screenshotEnabled.value();
    }

    public State state() {
        return lifecycle.state();
    }

    public boolean active() {
        return lifecycle.active();
    }

    public boolean stabilizesIncomingMessages() {
        return lifecycle.stabilizesIncomingMessages();
    }

    public boolean begin(ChatScreenshotSource source, double x, double y) {
        if (!enabled()) return false;
        cancel();
        var frozen = source.ralle$freezeScreenshotMessages();
        int scroll = frozen.initialScroll();
        var message = ChatScreenshotGeometry.messageAt(frozen, scroll, x, y);
        if (message.isEmpty()) return false;

        this.source = source;
        this.snapshot = frozen;
        this.snapshotScroll = scroll;
        this.anchorMessage = message.getAsInt();
        this.currentMessage = anchorMessage;
        this.controlHeld = true;
        this.leftHeld = true;
        this.pointerX = x;
        this.pointerY = y;
        this.lifecycle.beginDragging();
        this.targetBounds = calculateBounds();
        this.animationFrom = targetBounds;
        this.animationStarted = now();
        soundFeedback.selectionChanged(anchorMessage, currentMessage);
        return true;
    }

    public void drag(double x, double y) {
        pointerX = x;
        pointerY = y;
        if (state() != State.DRAGGING || !controlHeld || !leftHeld) return;
        updateRangeAtPointer();
    }

    public void releaseLeft() {
        if (state() != State.DRAGGING) return;
        leftHeld = false;
        stopOrFinalize();
    }

    public void releaseControl(boolean anyControlStillDown) {
        if (state() != State.DRAGGING) return;
        controlHeld = anyControlStillDown;
        if (!controlHeld) stopOrFinalize();
    }

    private void stopOrFinalize() {
        autoscroll.reset();
        if (!leftHeld && !controlHeld) {
            lifecycle.showPreview();
            targetBounds = calculateBounds();
            animationFrom = targetBounds;
        }
    }

    public boolean previewContains(double x, double y) {
        return state() == State.PREVIEW && targetBounds != null && targetBounds.contains(x, y);
    }

    public void cancelForManualScroll() {
        if (active()) cancel();
    }

    public void cancel() {
        boolean wasActive = active();
        copyGeneration++;
        lifecycle.cancel();
        source = null;
        snapshot = null;
        targetBounds = null;
        animationFrom = null;
        autoscroll.reset();
        soundFeedback.reset();
        if (wasActive && minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
    }

    public boolean copyPreview() {
        if (state() != State.PREVIEW) return false;
        lifecycle.beginCopying();
        long generation = ++copyGeneration;
        LineRange range = selectedRange();
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>(range.count());
        if (snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN) {
            for (int index = range.first(); index <= range.last(); index++) lines.add(snapshot.lines().get(index).content());
        } else {
            for (int index = range.last(); index >= range.first(); index--) lines.add(snapshot.lines().get(index).content());
        }
        capture.capture(new ChatScreenshotCapture.Request(
                lines,
                snapshot.visualWidth(),
                snapshot.lineHeight(),
                snapshot.textBaselineOffset(),
                snapshot.chatScale(),
                snapshot.textOpacity(),
                snapshot.alignment(),
                snapshot.shadow()
        ), new ChatScreenshotCapture.Completion() {
            @Override public void succeeded() {
                minecraft.execute(() -> {
                    if (generation == copyGeneration && state() == State.COPYING) {
                        soundFeedback.copySucceeded();
                        lifecycle.copySucceeded(now());
                    }
                });
            }
            @Override public void failed(Throwable error) {
                minecraft.execute(() -> {
                    if (generation != copyGeneration || state() != State.COPYING) return;
                    lifecycle.copyFailed();
                    if (minecraft.gui != null) {
                        RalleChatMessages.post(
                                minecraft,
                                Component.translatable("ralle.chat-screenshot.copy-failed", safeMessage(error))
                        );
                    }
                });
            }
        });
        return true;
    }

    public void tick() {
        soundFeedback.tick();
        if (!enabled()) {
            cancel();
            return;
        }
        if (lifecycle.copiedFadeFinished(now())) {
            cancel();
            return;
        }
        if (state() != State.DRAGGING || !controlHeld || !leftHeld || snapshot == null) {
            autoscroll.reset();
            return;
        }

        long now = now();
        OptionalInt nextAmount = autoscroll.nextAmount(snapshot, pointerX, pointerY, now);
        if (nextAmount.isEmpty()) return;

        int amount = nextAmount.getAsInt();
        int maximumSnapshotScroll = Math.max(0, snapshot.lines().size() - snapshot.linesPerPage());
        int desiredSnapshotScroll = snapshotScroll + amount;
        if (desiredSnapshotScroll < 0 || desiredSnapshotScroll > maximumSnapshotScroll) return;
        int before = source.ralle$screenshotScroll();
        source.ralle$selectionAutoscroll(amount);
        int after = source.ralle$screenshotScroll();
        if (after != before) {
            snapshotScroll += after - before;
            snapshotScroll = Math.max(0, Math.min(snapshotScroll, maximumSnapshotScroll));
            var hit = autoscroll.clampedHitPoint(snapshot, pointerX);
            updateRangeAtPointer(hit.x(), hit.y());
        }
    }

    public void renderLocalFill(ChatComponent.ChatGraphicsAccess graphics) {
        Rectangle bounds = currentBounds();
        if (bounds == null || bounds.height() == 0) return;
        int left = -4;
        int right = Math.max(left + 1, (int) Math.ceil(snapshot.visualWidth() / snapshot.chatScale()) - 4);
        int top = (int) Math.floor(bounds.top() / snapshot.chatScale());
        int bottom = (int) Math.ceil(bounds.bottom() / snapshot.chatScale());
        float opacity = lifecycle.overlayOpacity(now());
        if (opacity <= 0.0F) return;
        graphics.fill(left, top, right, bottom,
                ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_FILL, opacity));
    }

    public void renderOutline(GuiGraphics graphics) {
        Rectangle bounds = currentBounds();
        if (bounds == null || bounds.height() == 0) return;
        long now = now();
        float opacity = lifecycle.overlayOpacity(now);
        if (opacity <= 0.0F) return;
        int outlineColor = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_GOLD, opacity);
        graphics.enableScissor(snapshot.viewportLeft(), snapshot.viewportTop(), snapshot.viewportRight(), snapshot.viewportBottom());
        if (state() == State.DRAGGING) drawSolid(graphics, bounds, outlineColor);
        else drawDashed(graphics, bounds,
                (int) ((now / 60L) % (ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP)),
                outlineColor);
        if (state() == State.COPIED_FADING) drawCopiedConfirmation(graphics, bounds, opacity);
        graphics.disableScissor();
    }

    private void updateRangeAtPointer() {
        updateRangeAtPointer(pointerX, pointerY);
    }

    private void updateRangeAtPointer(double x, double y) {
        var message = ChatScreenshotGeometry.messageAt(snapshot, snapshotScroll, x, y);
        if (message.isEmpty() || message.getAsInt() == currentMessage) return;
        Rectangle before = currentBounds();
        currentMessage = message.getAsInt();
        animationFrom = before == null ? calculateBounds() : before;
        targetBounds = calculateBounds();
        animationStarted = now();
        soundFeedback.selectionChanged(anchorMessage, currentMessage);
    }

    private Rectangle calculateBounds() {
        return ChatScreenshotGeometry.visibleBounds(snapshot, snapshotScroll, selectedRange());
    }

    private LineRange selectedRange() {
        return ChatScreenshotGeometry.selectedLines(snapshot, anchorMessage, currentMessage);
    }

    private Rectangle currentBounds() {
        if (targetBounds == null) return null;
        if (state() != State.DRAGGING || !smoothExpansion.value() || animationFrom == null) return targetBounds;
        double progress = (now() - animationStarted) / (double) ChatScreenshotTokens.EXPANSION_MILLIS;
        double eased = ChatScreenshotGeometry.cubicEaseOut(progress);
        int top = interpolate(animationFrom.top(), targetBounds.top(), eased);
        int bottom = interpolate(animationFrom.bottom(), targetBounds.bottom(), eased);
        return new Rectangle(targetBounds.left(), top, targetBounds.right(), bottom);
    }

    private static void drawSolid(GuiGraphics graphics, Rectangle bounds, int color) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.top() + 1, color);
        graphics.fill(bounds.left(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.left(), bounds.top(), bounds.left() + 1, bounds.bottom(), color);
        graphics.fill(bounds.right() - 1, bounds.top(), bounds.right(), bounds.bottom(), color);
    }

    private static void drawDashed(GuiGraphics graphics, Rectangle bounds, int phase, int color) {
        int width = bounds.width();
        int height = bounds.height();
        for (int x = -phase; x < width; x += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(0, x);
            int end = Math.min(width, x + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) {
                graphics.fill(bounds.left() + start, bounds.top(), bounds.left() + end, bounds.top() + 1, color);
                graphics.fill(bounds.left() + start, bounds.bottom() - 1, bounds.left() + end, bounds.bottom(), color);
            }
        }
        for (int y = -phase; y < height; y += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(0, y);
            int end = Math.min(height, y + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) {
                graphics.fill(bounds.left(), bounds.top() + start, bounds.left() + 1, bounds.top() + end, color);
                graphics.fill(bounds.right() - 1, bounds.top() + start, bounds.right(), bounds.top() + end, color);
            }
        }
    }

    private void drawCopiedConfirmation(GuiGraphics graphics, Rectangle selection, float opacity) {
        Component label = RalleTypography.compactBody(Component.translatable("ralle.chat-screenshot.copied"));
        int textWidth = minecraft.font.width(label);
        int textHeight = minecraft.font.lineHeight;
        int faceWidth = textWidth + ChatScreenshotTokens.CONFIRMATION_HORIZONTAL_PADDING * 2
                + ChatScreenshotTokens.OUTLINE_WIDTH * 2;
        int faceHeight = textHeight + ChatScreenshotTokens.CONFIRMATION_VERTICAL_PADDING * 2
                + ChatScreenshotTokens.OUTLINE_WIDTH * 2;
        int outerWidth = faceWidth + ChatScreenshotTokens.CONFIRMATION_DEPTH;
        int outerHeight = faceHeight + ChatScreenshotTokens.CONFIRMATION_DEPTH;

        int left = centeredPosition(selection.left(), selection.right(), outerWidth,
                snapshot.viewportLeft(), snapshot.viewportRight());
        int top = centeredPosition(selection.top(), selection.bottom(), outerHeight,
                snapshot.viewportTop(), snapshot.viewportBottom());
        int faceRight = left + faceWidth;
        int faceBottom = top + faceHeight;

        graphics.fill(left, top, left + outerWidth, top + outerHeight,
                ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.CONFIRMATION_DEPTH_COLOR, opacity));
        graphics.fill(left, top, faceRight, faceBottom,
                ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.CONFIRMATION_FILL, opacity));

        int gold = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_GOLD, opacity);
        int highlight = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.CONFIRMATION_HIGHLIGHT, opacity);
        graphics.fill(left, top, faceRight, top + 1, highlight);
        graphics.fill(left, top, left + 1, faceBottom, highlight);
        graphics.fill(left, faceBottom - 1, faceRight, faceBottom, gold);
        graphics.fill(faceRight - 1, top, faceRight, faceBottom, gold);

        int textX = left + ChatScreenshotTokens.OUTLINE_WIDTH
                + ChatScreenshotTokens.CONFIRMATION_HORIZONTAL_PADDING;
        int textY = top + ChatScreenshotTokens.OUTLINE_WIDTH
                + ChatScreenshotTokens.CONFIRMATION_VERTICAL_PADDING;
        graphics.drawString(minecraft.font, label, textX, textY,
                ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.CONFIRMATION_TEXT, opacity), false);
    }

    private static int centeredPosition(int contentStart, int contentEnd, int size, int viewportStart, int viewportEnd) {
        int centered = contentStart + (contentEnd - contentStart - size) / 2;
        int viewportSize = viewportEnd - viewportStart;
        if (size > viewportSize) return centered;
        return Math.max(viewportStart, Math.min(centered, viewportEnd - size));
    }

    private static int interpolate(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
