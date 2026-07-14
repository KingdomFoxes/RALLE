package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatRenderLayoutTest {
    @Test
    void bottomUpKeepsVanillaLineIndices() {
        assertEquals(2, ChatRenderLayout.lineIndex(ChatBehaviorService.MessageDirection.BOTTOM_UP, 2, 8));
    }

    @Test
    void topDownPlacesTheNewestLineAtTheTopOfThePage() {
        assertEquals(7, ChatRenderLayout.lineIndex(ChatBehaviorService.MessageDirection.TOP_DOWN, 0, 8));
        assertEquals(5, ChatRenderLayout.lineIndex(ChatBehaviorService.MessageDirection.TOP_DOWN, 2, 8));
    }

    @Test
    void rightAlignmentOffsetsEachLineByItsOwnWidth() {
        assertEquals(0, ChatRenderLayout.horizontalOffset(ChatBehaviorService.HorizontalAlignment.LEFT, 200, 80));
        assertEquals(120, ChatRenderLayout.horizontalOffset(ChatBehaviorService.HorizontalAlignment.RIGHT, 200, 80));
        assertEquals(0, ChatRenderLayout.horizontalOffset(ChatBehaviorService.HorizontalAlignment.RIGHT, 80, 100));
    }
}
