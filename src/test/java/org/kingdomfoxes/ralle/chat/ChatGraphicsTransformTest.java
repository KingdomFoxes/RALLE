package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGraphicsTransformTest {
    @Test
    void noShadowOverridesAnyMessageShadowWithoutChangingInteractions() {
        var hover = new HoverEvent.ShowText(Component.literal("Details"));
        var original = Style.EMPTY.withColor(0xABCDEF).withShadowColor(0xFF123456).withHoverEvent(hover);

        var transformed = ChatGraphicsTransform.shadowStyle(original, ChatBehaviorService.TextShadow.NONE);

        assertEquals(Style.NO_SHADOW, transformed.getShadowColor());
        assertEquals(original.getColor(), transformed.getColor());
        assertSame(hover, transformed.getHoverEvent());
    }

    @Test
    void vanillaShadowLeavesTheStyleUntouched() {
        var original = Style.EMPTY.withColor(0xABCDEF);

        assertSame(original, ChatGraphicsTransform.shadowStyle(original, ChatBehaviorService.TextShadow.VANILLA));
    }

    @Test
    void partialFullShadowForcesOpaqueBlackWithoutChangingInteractions() {
        var hover = new HoverEvent.ShowText(Component.literal("Details"));
        var original = Style.EMPTY.withColor(0xABCDEF).withoutShadow().withHoverEvent(hover);

        var transformed = ChatGraphicsTransform.shadowStyle(original, ChatBehaviorService.TextShadow.PARTIAL_FULL);

        assertEquals(0xFF000000, transformed.getShadowColor());
        assertEquals(original.getColor(), transformed.getColor());
        assertSame(hover, transformed.getHoverEvent());
    }

    @Test
    void wrappedFullBatchesSixteenSoftLayersBeforeTheOriginalText() {
        var hover = new HoverEvent.ShowText(Component.literal("Details"));
        var original = Style.EMPTY
                .withColor(0xABCDEF)
                .withShadowColor(0xFF123456)
                .withBold(true)
                .withHoverEvent(hover)
                .withInsertion("insert me");
        var graphics = new RecordingGraphics();

        boolean hovered = ChatGraphicsTransform.renderWrappedFull(
                graphics,
                12,
                1.0F,
                FormattedCharSequence.forward("A", original),
                true
        );

        assertFalse(hovered, "Only the final real-text pass may determine interaction state");
        assertEquals(2, graphics.messages.size());

        var shadowPass = graphics.messages.getFirst();
        assertTrue(shadowPass.content() instanceof BatchedFullShadowSequence);
        assertEquals(16, BatchedFullShadowSequence.layerCount());
        assertEquals(0.25F, shadowPass.opacity());
        assertEquals(0x000000, shadowPass.style().getColor().getValue());
        assertEquals(Style.NO_SHADOW, shadowPass.style().getShadowColor());
        assertTrue(shadowPass.style().isBold());
        assertNull(shadowPass.style().getHoverEvent());
        assertNull(shadowPass.style().getClickEvent());
        assertNull(shadowPass.style().getInsertion());

        var mainPass = graphics.messages.getLast();
        assertEquals(new Offset(0.0F, 0.0F), new Offset(mainPass.x(), mainPass.y()));
        assertEquals(1.0F, mainPass.opacity());
        assertEquals(original.getColor(), mainPass.style().getColor());
        assertEquals(Style.NO_SHADOW, mainPass.style().getShadowColor());
        assertSame(hover, mainPass.style().getHoverEvent());
        assertEquals("insert me", mainPass.style().getInsertion());
        assertEquals(new Offset(0.0F, 0.0F), graphics.currentOffset());
    }

    @Test
    void wrappedFullSkipsVisualPassesDuringClickableTextCapture() {
        var original = Style.EMPTY.withColor(0xABCDEF).withInsertion("insert me");
        var graphics = new RecordingGraphics();

        ChatGraphicsTransform.renderWrappedFull(
                graphics,
                12,
                1.0F,
                FormattedCharSequence.forward("A", original),
                false
        );

        assertEquals(1, graphics.messages.size());
        assertEquals("insert me", graphics.messages.getFirst().style().getInsertion());
    }

    @Test
    void wrappedFullSkipsHaloAfterTheMessageHasNearlyFadedOut() {
        var graphics = new RecordingGraphics();

        ChatGraphicsTransform.renderWrappedFull(
                graphics,
                12,
                0.04F,
                FormattedCharSequence.forward("A", Style.EMPTY),
                true
        );

        assertEquals(1, graphics.messages.size());
        assertEquals(0.04F, graphics.messages.getFirst().opacity());
    }

    private static Style firstStyle(FormattedCharSequence content) {
        var result = new AtomicReference<Style>();
        content.accept((index, style, codePoint) -> {
            result.set(style);
            return false;
        });
        return result.get();
    }

    private static final class RecordingGraphics implements ChatComponent.ChatGraphicsAccess {
        private final Matrix3x2f pose = new Matrix3x2f();
        private final List<MessageCall> messages = new ArrayList<>();

        @Override
        public void updatePose(Consumer<Matrix3x2f> consumer) {
            consumer.accept(pose);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {}

        @Override
        public boolean handleMessage(int y, float opacity, FormattedCharSequence content) {
            messages.add(new MessageCall(pose.m20(), pose.m21(), opacity, content, firstStyle(content)));
            return messages.size() == 1;
        }

        @Override
        public void handleTag(int left, int top, int right, int bottom, float opacity, GuiMessageTag tag) {}

        @Override
        public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {}

        private Offset currentOffset() {
            return new Offset(pose.m20(), pose.m21());
        }
    }

    private record MessageCall(
            float x,
            float y,
            float opacity,
            FormattedCharSequence content,
            Style style
    ) {}

    private record Offset(float x, float y) {}
}
