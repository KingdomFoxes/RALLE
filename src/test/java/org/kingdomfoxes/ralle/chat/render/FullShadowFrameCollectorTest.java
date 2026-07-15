package org.kingdomfoxes.ralle.chat.render;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullShadowFrameCollectorTest {
    @Test
    void visualMaskStyleKeepsGlyphGeometryAndDropsSemanticMetadata() {
        var original = Style.EMPTY
                .withColor(0xABCDEF)
                .withShadowColor(0xFF123456)
                .withBold(true)
                .withItalic(true)
                .withUnderlined(true)
                .withStrikethrough(true)
                .withObfuscated(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Details")))
                .withInsertion("insert me");

        var mask = FullShadowFrameCollector.visualShadowStyle(original);

        assertEquals(0x000000, mask.getColor().getValue());
        assertEquals(Style.NO_SHADOW, mask.getShadowColor());
        assertTrue(mask.isBold());
        assertTrue(mask.isItalic());
        assertTrue(mask.isUnderlined());
        assertTrue(mask.isStrikethrough());
        assertTrue(mask.isObfuscated());
        assertNull(mask.getHoverEvent());
        assertNull(mask.getClickEvent());
        assertNull(mask.getInsertion());
    }
}
