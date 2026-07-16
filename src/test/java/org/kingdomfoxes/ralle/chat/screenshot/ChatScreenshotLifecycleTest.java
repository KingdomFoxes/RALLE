package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService.State;

class ChatScreenshotLifecycleTest {
    @Test
    void successfulCopyFadesBeforeReturningToIdle() {
        var lifecycle = new ChatScreenshotLifecycle();
        lifecycle.beginDragging();
        assertEquals(State.DRAGGING, lifecycle.state());
        lifecycle.showPreview();
        assertEquals(State.PREVIEW, lifecycle.state());
        lifecycle.beginCopying();
        assertEquals(State.COPYING, lifecycle.state());

        lifecycle.copySucceeded(1_000L);
        assertEquals(State.COPIED_FADING, lifecycle.state());
        assertTrue(lifecycle.active());
        assertTrue(lifecycle.stabilizesIncomingMessages());
        assertEquals(1.0F, lifecycle.overlayOpacity(1_000L));
        assertEquals(0.5F, lifecycle.overlayOpacity(1_125L));
        assertEquals(0.0F, lifecycle.overlayOpacity(1_250L));
        assertFalse(lifecycle.copiedFadeFinished(1_249L));
        assertTrue(lifecycle.copiedFadeFinished(1_250L));

        lifecycle.cancel();
        assertEquals(State.IDLE, lifecycle.state());
        assertFalse(lifecycle.active());
    }

    @Test
    void failedCopyReturnsToRetryablePreviewWithoutFading() {
        var lifecycle = new ChatScreenshotLifecycle();
        lifecycle.beginDragging();
        lifecycle.showPreview();
        lifecycle.beginCopying();
        lifecycle.copyFailed();

        assertEquals(State.PREVIEW, lifecycle.state());
        assertEquals(1.0F, lifecycle.overlayOpacity(Long.MAX_VALUE));
        assertFalse(lifecycle.copiedFadeFinished(Long.MAX_VALUE));
    }

    @Test
    void cancellationClearsCopiedFadeImmediately() {
        var lifecycle = new ChatScreenshotLifecycle();
        lifecycle.beginDragging();
        lifecycle.showPreview();
        lifecycle.beginCopying();
        lifecycle.copySucceeded(2_000L);
        lifecycle.cancel();

        assertEquals(State.IDLE, lifecycle.state());
        assertFalse(lifecycle.stabilizesIncomingMessages());
    }

    @Test
    void presentationColorsUseTheSameClampedOpacityFactor() {
        assertEquals(0x80041330, ChatScreenshotTokens.withOpacity(0xFF041330, 0.5F));
        assertEquals(0x73041330, ChatScreenshotTokens.withOpacity(0xE6041330, 0.5F));
        assertEquals(0x00041330, ChatScreenshotTokens.withOpacity(0xFF041330, -1.0F));
        assertEquals(0xFF041330, ChatScreenshotTokens.withOpacity(0xFF041330, 2.0F));
    }
}
