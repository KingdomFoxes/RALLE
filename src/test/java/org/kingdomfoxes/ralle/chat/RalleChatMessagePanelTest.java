package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotTokens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleChatMessagePanelTest {
    @Test
    void wrappedSampleUsesOneNavyPanelWithSolidGoldOutline() {
        var graphics = new RecordingGraphics();
        var lines = List.of(
                line(true),
                line(false),
                line(false)
        );

        RalleChatMessagePanel.render(
                graphics,
                lines,
                0,
                10,
                9,
                100,
                320,
                1.0,
                ChatBehaviorService.MessageDirection.BOTTOM_UP,
                0,
                true
        );

        assertEquals(List.of(
                new Fill(-4, 71, 316, 100, ChatScreenshotTokens.SELECTION_FILL),
                new Fill(-4, 71, 316, 72, ChatScreenshotTokens.SELECTION_GOLD),
                new Fill(-4, 99, 316, 100, ChatScreenshotTokens.SELECTION_GOLD),
                new Fill(-4, 71, -3, 100, ChatScreenshotTokens.SELECTION_GOLD),
                new Fill(315, 71, 316, 100, ChatScreenshotTokens.SELECTION_GOLD)
        ), graphics.fills);
    }

    @Test
    void ordinaryChatLinesDoNotReceiveAPanel() {
        var graphics = new RecordingGraphics();
        var ordinaryTag = GuiMessageTag.system();

        RalleChatMessagePanel.render(
                graphics,
                List.of(new GuiMessage.Line(0, text(), ordinaryTag, true)),
                0,
                10,
                9,
                100,
                320,
                1.0,
                ChatBehaviorService.MessageDirection.BOTTOM_UP,
                0,
                true
        );

        assertTrue(graphics.fills.isEmpty());
    }

    private static GuiMessage.Line line(boolean endOfEntry) {
        return new GuiMessage.Line(0, text(), sampleTag(), endOfEntry);
    }

    private static GuiMessageTag sampleTag() {
        return RalleChatMessages.tag();
    }

    private static FormattedCharSequence text() {
        return FormattedCharSequence.forward("sample", Style.EMPTY);
    }

    private static final class RecordingGraphics implements ChatComponent.ChatGraphicsAccess {
        private final List<Fill> fills = new ArrayList<>();

        @Override
        public void updatePose(Consumer<Matrix3x2f> consumer) {}

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left, top, right, bottom, color));
        }

        @Override
        public boolean handleMessage(int y, float opacity, FormattedCharSequence content) {
            return false;
        }

        @Override
        public void handleTag(int left, int top, int right, int bottom, float opacity, GuiMessageTag tag) {}

        @Override
        public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {}
    }

    private record Fill(int left, int top, int right, int bottom, int color) {}
}
