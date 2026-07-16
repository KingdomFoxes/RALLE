package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotGeometry.LineRange;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotGeometry.Rectangle;
import org.kingdomfoxes.ralle.sound.ChatSelectionSoundPlayer;

import java.util.ArrayList;
import java.util.OptionalInt;

public final class ChatScreenshotService {
    public enum State { IDLE, DRAGGING, PREVIEW, COPYING }

    private final Minecraft minecraft;
    private final BooleanSetting chatEnabled;
    private final BooleanSetting screenshotEnabled;
    private final BooleanSetting smoothExpansion;
    private final ChatScreenshotCapture capture;
    private final ChatSelectionSoundFeedback soundFeedback;

    private State state = State.IDLE;
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
        this.chatEnabled = settings.setting("chat-enabled", BooleanSetting.class);
        this.screenshotEnabled = settings.setting("chat-screenshot-enabled", BooleanSetting.class);
        this.smoothExpansion = settings.setting("chat-screenshot-smooth-expansion", BooleanSetting.class);
        this.capture = capture;
        this.soundFeedback = new ChatSelectionSoundFeedback(soundPlayer);
    }

    public boolean enabled() {
        return chatEnabled.value() && screenshotEnabled.value();
    }

    public State state() {
        return state;
    }

    public boolean active() {
        return state != State.IDLE;
    }

    public boolean stabilizesIncomingMessages() {
        return state == State.DRAGGING || state == State.PREVIEW || state == State.COPYING;
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
        this.state = State.DRAGGING;
        this.targetBounds = calculateBounds();
        this.animationFrom = targetBounds;
        this.animationStarted = now();
        soundFeedback.selectionChanged(anchorMessage, currentMessage);
        return true;
    }

    public void drag(double x, double y) {
        pointerX = x;
        pointerY = y;
        if (state != State.DRAGGING || !controlHeld || !leftHeld) return;
        updateRangeAtPointer();
    }

    public void releaseLeft() {
        if (state != State.DRAGGING) return;
        leftHeld = false;
        stopOrFinalize();
    }

    public void releaseControl(boolean anyControlStillDown) {
        if (state != State.DRAGGING) return;
        controlHeld = anyControlStillDown;
        if (!controlHeld) stopOrFinalize();
    }

    private void stopOrFinalize() {
        autoscroll.reset();
        if (!leftHeld && !controlHeld) {
            state = State.PREVIEW;
            targetBounds = calculateBounds();
            animationFrom = targetBounds;
        }
    }

    public boolean previewContains(double x, double y) {
        return state == State.PREVIEW && targetBounds != null && targetBounds.contains(x, y);
    }

    public void cancelForManualScroll() {
        if (active()) cancel();
    }

    public void cancel() {
        boolean wasActive = active();
        copyGeneration++;
        state = State.IDLE;
        source = null;
        snapshot = null;
        targetBounds = null;
        animationFrom = null;
        autoscroll.reset();
        soundFeedback.reset();
        if (wasActive && minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
    }

    public boolean copyPreview() {
        if (state != State.PREVIEW) return false;
        state = State.COPYING;
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
                    if (generation == copyGeneration && state == State.COPYING) {
                        soundFeedback.copySucceeded();
                        cancel();
                    }
                });
            }
            @Override public void failed(Throwable error) {
                minecraft.execute(() -> {
                    if (generation != copyGeneration || state != State.COPYING) return;
                    state = State.PREVIEW;
                    if (minecraft.gui != null) {
                        minecraft.gui.getChat().addMessage(Component.translatable("ralle.chat-screenshot.copy-failed", safeMessage(error)));
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
        if (state != State.DRAGGING || !controlHeld || !leftHeld || snapshot == null) {
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
        graphics.fill(left, top, right, bottom, ChatScreenshotTokens.SELECTION_FILL);
    }

    public void renderOutline(GuiGraphics graphics) {
        Rectangle bounds = currentBounds();
        if (bounds == null || bounds.height() == 0) return;
        graphics.enableScissor(snapshot.viewportLeft(), snapshot.viewportTop(), snapshot.viewportRight(), snapshot.viewportBottom());
        if (state == State.DRAGGING) drawSolid(graphics, bounds);
        else drawDashed(graphics, bounds, (int) ((now() / 60L) % (ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP)));
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
        if (state != State.DRAGGING || !smoothExpansion.value() || animationFrom == null) return targetBounds;
        double progress = (now() - animationStarted) / (double) ChatScreenshotTokens.EXPANSION_MILLIS;
        double eased = ChatScreenshotGeometry.cubicEaseOut(progress);
        int top = interpolate(animationFrom.top(), targetBounds.top(), eased);
        int bottom = interpolate(animationFrom.bottom(), targetBounds.bottom(), eased);
        return new Rectangle(targetBounds.left(), top, targetBounds.right(), bottom);
    }

    private static void drawSolid(GuiGraphics graphics, Rectangle bounds) {
        int color = ChatScreenshotTokens.SELECTION_GOLD;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.top() + 1, color);
        graphics.fill(bounds.left(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.left(), bounds.top(), bounds.left() + 1, bounds.bottom(), color);
        graphics.fill(bounds.right() - 1, bounds.top(), bounds.right(), bounds.bottom(), color);
    }

    private static void drawDashed(GuiGraphics graphics, Rectangle bounds, int phase) {
        int width = bounds.width();
        int height = bounds.height();
        for (int x = -phase; x < width; x += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(0, x);
            int end = Math.min(width, x + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) {
                graphics.fill(bounds.left() + start, bounds.top(), bounds.left() + end, bounds.top() + 1, ChatScreenshotTokens.SELECTION_GOLD);
                graphics.fill(bounds.left() + start, bounds.bottom() - 1, bounds.left() + end, bounds.bottom(), ChatScreenshotTokens.SELECTION_GOLD);
            }
        }
        for (int y = -phase; y < height; y += ChatScreenshotTokens.DASH_LENGTH + ChatScreenshotTokens.DASH_GAP) {
            int start = Math.max(0, y);
            int end = Math.min(height, y + ChatScreenshotTokens.DASH_LENGTH);
            if (end > start) {
                graphics.fill(bounds.left(), bounds.top() + start, bounds.left() + 1, bounds.top() + end, ChatScreenshotTokens.SELECTION_GOLD);
                graphics.fill(bounds.right() - 1, bounds.top() + start, bounds.right(), bounds.top() + end, ChatScreenshotTokens.SELECTION_GOLD);
            }
        }
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
