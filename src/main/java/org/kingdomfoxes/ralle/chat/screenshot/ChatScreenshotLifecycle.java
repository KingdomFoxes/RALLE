package org.kingdomfoxes.ralle.chat.screenshot;

import static org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService.State;

/** Deterministic state and timing for chat screenshot selection feedback. */
final class ChatScreenshotLifecycle {
    private State state = State.IDLE;
    private long copiedFadeStartedAt;

    State state() {
        return state;
    }

    boolean active() {
        return state != State.IDLE;
    }

    boolean stabilizesIncomingMessages() {
        return state == State.DRAGGING
                || state == State.PREVIEW
                || state == State.COPYING
                || state == State.COPIED_FADING;
    }

    void beginDragging() {
        state = State.DRAGGING;
    }

    void showPreview() {
        state = State.PREVIEW;
    }

    void beginCopying() {
        state = State.COPYING;
    }

    void copySucceeded(long now) {
        state = State.COPIED_FADING;
        copiedFadeStartedAt = now;
    }

    void copyFailed() {
        state = State.PREVIEW;
    }

    void cancel() {
        state = State.IDLE;
        copiedFadeStartedAt = 0L;
    }

    float overlayOpacity(long now) {
        if (state != State.COPIED_FADING) return 1.0F;
        double elapsed = Math.max(0L, now - copiedFadeStartedAt);
        return (float) Math.max(0.0, 1.0 - elapsed / ChatScreenshotTokens.COPIED_FADE_MILLIS);
    }

    boolean copiedFadeFinished(long now) {
        return state == State.COPIED_FADING
                && now - copiedFadeStartedAt >= ChatScreenshotTokens.COPIED_FADE_MILLIS;
    }
}
