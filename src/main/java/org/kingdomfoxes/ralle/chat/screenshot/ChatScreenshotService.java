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
import java.util.List;
import java.util.OptionalInt;

public final class ChatScreenshotService {
    public enum State { IDLE, DRAGGING, PREVIEW, COPYING, COPIED_FADING }

    private final Minecraft minecraft;
    private final BooleanSetting screenshotEnabled;
    private final BooleanSetting snapToText;
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
    private LineRange animationRange;
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
        this.snapToText = settings.setting("chat-screenshot-snap-to-text", BooleanSetting.class);
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
        this.animationRange = selectedRange();
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
            animationRange = selectedRange();
        }
    }

    public boolean previewContains(double x, double y) {
        return state() == State.PREVIEW && currentVisualBounds().stream().anyMatch(bounds -> bounds.contains(x, y));
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
        animationRange = null;
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
        int maximumTextWidth = ChatScreenshotGeometry.maximumTextWidth(snapshot, range);
        boolean snapped = snapToText.value();
        int visualWidth = snapped
                ? ChatScreenshotGeometry.snappedCaptureVisualWidth(maximumTextWidth, snapshot.chatScale())
                : snapshot.visualWidth();
        int contentWidth = snapped
                ? maximumTextWidth
                : Math.max(1, (int) Math.ceil(snapshot.visualWidth() / snapshot.chatScale()));
        int textOffset = snapped
                ? ChatScreenshotGeometry.snappedCaptureTextOffset(snapshot.chatScale())
                : ChatScreenshotTokens.CHAT_TEXT_OFFSET;
        capture.capture(new ChatScreenshotCapture.Request(
                lines,
                visualWidth,
                contentWidth,
                textOffset,
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
        float opacity = lifecycle.overlayOpacity(now());
        if (opacity <= 0.0F) return;
        int color = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_FILL, opacity);
        for (Rectangle bounds : currentVisualBounds()) {
            int left = (int) Math.floor((bounds.left() - snapshot.viewportLeft()) / snapshot.chatScale())
                    - ChatScreenshotTokens.CHAT_TEXT_OFFSET;
            int right = Math.max(left + 1,
                    (int) Math.ceil((bounds.right() - snapshot.viewportLeft()) / snapshot.chatScale())
                            - ChatScreenshotTokens.CHAT_TEXT_OFFSET);
            int top = (int) Math.floor(bounds.top() / snapshot.chatScale());
            int bottom = (int) Math.ceil(bounds.bottom() / snapshot.chatScale());
            graphics.fill(left, top, right, bottom, color);
        }
    }

    public void renderOutline(GuiGraphics graphics) {
        Rectangle bounds = currentBounds();
        if (bounds == null || bounds.height() == 0) return;
        List<Rectangle> visualBounds = currentVisualBounds();
        if (visualBounds.isEmpty()) return;
        long now = now();
        float opacity = lifecycle.overlayOpacity(now);
        if (opacity <= 0.0F) return;
        int outlineColor = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_GOLD, opacity);
        graphics.enableScissor(snapshot.viewportLeft(), snapshot.viewportTop(), snapshot.viewportRight(), snapshot.viewportBottom());
        if (visualBounds.size() == 1) {
            if (state() == State.DRAGGING) drawSolid(graphics, visualBounds.getFirst(), outlineColor);
            else drawDashed(graphics, visualBounds.getFirst(),
                    (int) ((now / 60L) % (ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP)),
                    outlineColor);
        } else {
            int phase = (int) ((now / 60L) % (ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP));
            drawContour(graphics, visualBounds, state() == State.DRAGGING, phase, outlineColor);
        }
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
        LineRange beforeRange = selectedRange();
        currentMessage = message.getAsInt();
        LineRange afterRange = selectedRange();
        animationRange = new LineRange(
                Math.min(beforeRange.first(), afterRange.first()),
                Math.max(beforeRange.last(), afterRange.last())
        );
        animationFrom = before == null ? calculateBounds() : before;
        targetBounds = calculateBounds();
        animationStarted = now();
        soundFeedback.selectionChanged(anchorMessage, currentMessage);
    }

    private Rectangle calculateBounds() {
        LineRange range = selectedRange();
        Rectangle bounds = ChatScreenshotGeometry.visibleBounds(snapshot, snapshotScroll, range);
        if (!snapToText.value() || bounds.height() == 0) return bounds;
        return ChatScreenshotGeometry.snapToTextBounds(
                snapshot,
                bounds,
                ChatScreenshotGeometry.maximumTextWidth(snapshot, range)
        );
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

    private List<Rectangle> currentVisualBounds() {
        Rectangle clip = currentBounds();
        if (clip == null || clip.height() == 0) return List.of();
        if (!snapToText.value()) return List.of(clip);

        LineRange range = selectedRange();
        if (state() == State.DRAGGING && smoothExpansion.value() && animationRange != null
                && now() - animationStarted < ChatScreenshotTokens.EXPANSION_MILLIS) {
            range = animationRange;
        }
        var result = new ArrayList<Rectangle>();
        for (Rectangle bounds : ChatScreenshotGeometry.snappedLineBounds(snapshot, snapshotScroll, range)) {
            int top = Math.max(bounds.top(), clip.top());
            int bottom = Math.min(bounds.bottom(), clip.bottom());
            if (bottom > top) result.add(new Rectangle(bounds.left(), top, bounds.right(), bottom));
        }
        return List.copyOf(result);
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

    private static void drawContour(
            GuiGraphics graphics,
            List<Rectangle> bounds,
            boolean solid,
            int phase,
            int color
    ) {
        Rectangle first = bounds.getFirst();
        drawHorizontalSegment(graphics, first.left(), first.right(), first.top(), solid, phase, color);
        for (int index = 0; index < bounds.size(); index++) {
            Rectangle current = bounds.get(index);
            drawVerticalSegment(graphics, current.left(), current.top(), current.bottom(), solid, phase, color);
            drawVerticalSegment(graphics, current.right() - 1, current.top(), current.bottom(), solid, phase, color);
            if (index == 0) continue;
            Rectangle previous = bounds.get(index - 1);
            int boundary = current.top();
            if (current.left() != previous.left()) {
                drawHorizontalSegment(graphics, Math.min(current.left(), previous.left()),
                        Math.max(current.left(), previous.left()) + 1, boundary, solid, phase, color);
            }
            if (current.right() != previous.right()) {
                drawHorizontalSegment(graphics, Math.min(current.right(), previous.right()) - 1,
                        Math.max(current.right(), previous.right()), boundary, solid, phase, color);
            }
        }
        Rectangle last = bounds.getLast();
        drawHorizontalSegment(graphics, last.left(), last.right(), last.bottom() - 1, solid, phase, color);
    }

    private static void drawHorizontalSegment(
            GuiGraphics graphics,
            int left,
            int right,
            int y,
            boolean solid,
            int phase,
            int color
    ) {
        if (right <= left) return;
        if (solid) {
            graphics.fill(left, y, right, y + 1, color);
            return;
        }
        for (int x = left - phase; x < right; x += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(left, x);
            int end = Math.min(right, x + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) graphics.fill(start, y, end, y + 1, color);
        }
    }

    private static void drawVerticalSegment(
            GuiGraphics graphics,
            int x,
            int top,
            int bottom,
            boolean solid,
            int phase,
            int color
    ) {
        if (bottom <= top) return;
        if (solid) {
            graphics.fill(x, top, x + 1, bottom, color);
            return;
        }
        for (int y = top - phase; y < bottom; y += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(top, y);
            int end = Math.min(bottom, y + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) graphics.fill(x, start, x + 1, end, color);
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
