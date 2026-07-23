package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatScrollbarGraphicsTest {
    @Test
    void hiddenScrollbarDropsOnlyVanillaScrollbarFills() {
        var delegate = new RecordingGraphics();
        var graphics = ChatScrollbarGraphics.wrap(delegate, 320, true);

        graphics.fill(-4, -20, 328, -10, 1);
        graphics.fill(324, -40, 326, -10, 2);
        graphics.fill(326, -40, 325, -10, 3);
        graphics.fill(-2, 0, 324, 9, 4);

        assertEquals(List.of(
                new Fill(-4, -20, 328, -10, 1),
                new Fill(-2, 0, 324, 9, 4)
        ), delegate.fills);
    }

    @Test
    void visibleScrollbarReturnsOriginalGraphics() {
        var delegate = new RecordingGraphics();

        assertSame(delegate, ChatScrollbarGraphics.wrap(delegate, 320, false));
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
