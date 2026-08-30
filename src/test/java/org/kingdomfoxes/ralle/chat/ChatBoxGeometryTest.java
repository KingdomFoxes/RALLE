package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatBoxGeometryTest {
    @Test
    void renderedWidthIncludesMinecraftsCompleteBackgroundAtEveryChatScale() {
        assertEquals(332, ChatBoxGeometry.renderedWidth(320, 1.0));
        assertEquals(332, ChatBoxGeometry.renderedWidth(320, 0.9));
        assertEquals(326, ChatBoxGeometry.renderedWidth(320, 0.5));
    }

    @Test
    void editorWidthConvertsBackWithoutGrowingTheRenderedChatBox() {
        for (double scale : new double[]{0.5, 0.75, 0.9, 1.0}) {
            int renderedWidth = ChatBoxGeometry.renderedWidth(320, scale);
            int chatWidth = ChatBoxGeometry.chatWidthForRenderedWidth(renderedWidth, scale);

            assertEquals(renderedWidth, ChatBoxGeometry.renderedWidth(chatWidth, scale));
        }
    }
}
