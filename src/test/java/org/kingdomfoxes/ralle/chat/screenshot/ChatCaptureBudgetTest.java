package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class ChatCaptureBudgetTest {
    @Test void checksDeviceLimitsPixelBudgetAndOverflowBeforeAllocation() {
        assertEquals(new ChatCaptureBudget.Size(2048, 2048), ChatCaptureBudget.validate(1024, 1024, 2, 4096));
        assertThrows(IllegalArgumentException.class, () -> ChatCaptureBudget.validate(1024, 1025, 2, 4096));
        assertThrows(IllegalArgumentException.class, () -> ChatCaptureBudget.validate(4096, 1, 1, 2048));
        assertThrows(IllegalArgumentException.class, () -> ChatCaptureBudget.validate(Integer.MAX_VALUE, Integer.MAX_VALUE, 8, 16384));
        assertThrows(IllegalArgumentException.class, () -> ChatCaptureBudget.validate(1, 1, 0, 16384));
        assertThrows(IllegalArgumentException.class, () -> ChatScreenshotGeometry.captureVisualHeight(Integer.MAX_VALUE, 20, 1));
        assertThrows(IllegalArgumentException.class, () -> ChatScreenshotGeometry.captureVisualHeight(1, 10, Double.NaN));
    }

    @Test void flipsAndUnpremultipliesInOnePassWithoutDependingOnByteOrder() {
        byte[] pixels = {64, 32, 0, (byte)128, 1, 2, 3, (byte)255, 0, 0, 0, 0};
        assertArrayEquals(new byte[]{0,0,0,0, 1,2,3,(byte)255, 127,63,0,(byte)128},
                CapturePixels.toStraightAlphaRgba(ByteBuffer.wrap(pixels), 1, 3));
    }
}
