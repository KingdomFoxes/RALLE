package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotGeometry;

class ChatScreenshotOutlineTest {
    @Test
    void startsAtBottomRightAndTravelsUpThenAcrossTheTop() {
        assertEquals(List.of(
                        new ChatScreenshotOutline.Segment(29, 39, 0, -1, 20),
                        new ChatScreenshotOutline.Segment(28, 20, -1, 0, 19),
                        new ChatScreenshotOutline.Segment(10, 21, 0, 1, 19),
                        new ChatScreenshotOutline.Segment(11, 39, 1, 0, 18)
                ),
                ChatScreenshotOutline.perimeter(List.of(new ChatScreenshotGeometry.Rectangle(10, 20, 30, 40))));
    }

    @Test
    void equalWidthRowsShareOneContinuousSideInsteadOfRestartingEachRow() {
        var bounds = List.of(
                new ChatScreenshotGeometry.Rectangle(10, 0, 30, 10),
                new ChatScreenshotGeometry.Rectangle(10, 10, 30, 20)
        );

        assertEquals(List.of(
                        new ChatScreenshotOutline.Segment(29, 19, 0, -1, 20),
                        new ChatScreenshotOutline.Segment(28, 0, -1, 0, 19),
                        new ChatScreenshotOutline.Segment(10, 1, 0, 1, 19),
                        new ChatScreenshotOutline.Segment(11, 19, 1, 0, 18)
                ),
                ChatScreenshotOutline.perimeter(bounds));
    }
}
