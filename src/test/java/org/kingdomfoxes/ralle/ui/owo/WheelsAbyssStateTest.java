package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelsAbyssStateTest {
    @Test
    void scrollProgressAudioVolumeAndFadeScaleLinearlyAndClamp() {
        assertEquals(0.0, WheelsAbyssState.scrollProgress(0, 0));
        assertEquals(0.0, WheelsAbyssState.scrollProgress(-50, 3000));
        assertEquals(0.5, WheelsAbyssState.scrollProgress(1500, 3000), 1e-6);
        assertEquals(1.0, WheelsAbyssState.scrollProgress(4000, 3000));

        assertEquals(0.0F, WheelsAbyssState.audioVolume(0.0));
        assertEquals(0.25F, WheelsAbyssState.audioVolume(0.25), 1e-6);
        assertEquals(0.75F, WheelsAbyssState.audioVolume(0.75), 1e-6);
        assertEquals(1.0F, WheelsAbyssState.audioVolume(1.5));

        assertEquals(0, WheelsAbyssState.fadeAlpha(0.0));
        assertEquals(128, WheelsAbyssState.fadeAlpha(0.5));
        assertEquals(255, WheelsAbyssState.fadeAlpha(1.0));
        assertEquals(0, WheelsAbyssState.fadeAlpha(Double.NaN));
    }

    @Test
    void eyeBoundsCenterNearBottomAndMatchHitbox() {
        var bounds = WheelsAbyssState.eyeBounds(421, WheelsAbyssState.AND_THEN_HEIGHT);
        assertEquals(WheelsAbyssState.EYE_MAX_DISPLAY_WIDTH, bounds.width());
        assertEquals((421 - bounds.width()) / 2, bounds.left());
        assertEquals(WheelsAbyssState.AND_THEN_HEIGHT - bounds.height() - WheelsAbyssState.EYE_BOTTOM_MARGIN, bounds.top());

        assertTrue(bounds.contains(bounds.left() + bounds.width() / 2.0, bounds.top() + bounds.height() / 2.0));
        assertFalse(bounds.contains(bounds.left() - 1, bounds.top() + 10));
        assertFalse(bounds.contains(bounds.left() + 10, bounds.top() + bounds.height()));
    }

    @Test
    void eyeBoundsShrinkGracefullyOnNarrowViewports() {
        var narrow = WheelsAbyssState.eyeBounds(160, WheelsAbyssState.AND_THEN_HEIGHT);
        assertEquals(144, narrow.width());
        assertTrue(narrow.left() >= 0);
        assertTrue(narrow.left() + narrow.width() <= 160);
    }

    @Test
    void andThenStepsThroughThreeChunksEveryQuarterSecondAcrossViewport() {
        assertEquals(0, WheelsAbyssState.andThenChunk(0));
        assertEquals(0, WheelsAbyssState.andThenChunk(249));
        assertEquals(1, WheelsAbyssState.andThenChunk(250));
        assertEquals(1, WheelsAbyssState.andThenChunk(499));
        assertEquals(2, WheelsAbyssState.andThenChunk(500));
        assertEquals(2, WheelsAbyssState.andThenChunk(749));
        assertEquals(0, WheelsAbyssState.andThenChunk(750));

        int componentX = 200;
        int componentWidth = 429;
        int pad = 8;
        int x0 = WheelsAbyssState.andThenDrawX(componentX, componentWidth, pad, 0);
        int x1 = WheelsAbyssState.andThenDrawX(componentX, componentWidth, pad, 1);
        int x2 = WheelsAbyssState.andThenDrawX(componentX, componentWidth, pad, 2);

        // Chunk 0 aligns left edge, chunk 1 centers, chunk 2 aligns right edge — full coverage with no gaps.
        assertEquals(componentX - pad, x0);
        assertEquals(componentX + (componentWidth - WheelsAbyssState.AND_THEN_WIDTH) / 2, x1);
        assertEquals(componentX + componentWidth + pad - WheelsAbyssState.AND_THEN_WIDTH, x2);
        assertTrue(x0 > x1 && x1 > x2);
    }
}
