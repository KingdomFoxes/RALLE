package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void fullShadowForcesOpaqueBlackWithoutChangingInteractions() {
        var hover = new HoverEvent.ShowText(Component.literal("Details"));
        var original = Style.EMPTY.withColor(0xABCDEF).withoutShadow().withHoverEvent(hover);

        var transformed = ChatGraphicsTransform.shadowStyle(original, ChatBehaviorService.TextShadow.FULL);

        assertEquals(0xFF000000, transformed.getShadowColor());
        assertEquals(original.getColor(), transformed.getColor());
        assertSame(hover, transformed.getHoverEvent());
    }
}
