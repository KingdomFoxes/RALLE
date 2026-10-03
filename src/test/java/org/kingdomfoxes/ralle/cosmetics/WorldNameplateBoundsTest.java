package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldNameplateBoundsTest {
    @Test void failingPreparedGlyphProviderStillProducesAPaddedVisiblePlate() {
        var font = new Font(null) {
            @Override public int width(net.minecraft.network.chat.FormattedText text) { return 36; }
            @Override public PreparedText prepareText(FormattedCharSequence text, float x, float y, int color,
                                                      boolean shadow, boolean includeEmpty, int background) {
                throw new IllegalStateException("Incompatible glyph provider");
            }
        };
        var bounds = WorldNameplateBounds.measure(font, Component.literal("Player"), false);
        assertEquals(new WorldNameplateBounds(-20, 20, -1, 9), bounds);
        assertEquals(42, bounds.centeredOuterWidth());
    }

    @Test void ordinaryUsernameHasPaddingOutsideItsFullWidth() {
        var font = CosmeticTestFont.create();
        var label = Component.literal("Player");
        var bounds = WorldNameplateBounds.measure(font, label, false);
        assertEquals(-20, bounds.left());
        assertEquals(20, bounds.right());
        assertEquals(-1, bounds.top());
        assertEquals(9, bounds.bottom());
    }

    @Test void prefixAndSuffixIconsFitEvenWhenTheirInkExtendsPastTheirAdvance() {
        var font = CosmeticTestFont.create();
        for (String text : new String[] {"\ue001Player", "Player\ue001"}) {
            var label = Component.literal(text);
            float x = -font.width(label) / 2f;
            var ink = font.prepareText(label.getVisualOrderText(), x, 0, -1, false, false, 0).bounds();
            var bounds = WorldNameplateBounds.measure(font, label, false);
            assertTrue(bounds.left() <= ink.left() - 2);
            assertTrue(bounds.right() >= ink.right() + 2);
            assertEquals(-4, bounds.top());
            assertEquals(11, bounds.bottom());
            assertTrue(bounds.centeredOuterWidth() / 2f >= Math.max(Math.abs(bounds.left()), Math.abs(bounds.right())) + 1);
        }
    }

    @Test void extraEarsOffsetMovesTheWholePlateWithTheVanillaText() {
        var font = CosmeticTestFont.create();
        var label = Component.literal("Player\ue001");
        var ordinary = WorldNameplateBounds.measure(font, label, false);
        var shifted = WorldNameplateBounds.measure(font, label, true);
        assertEquals(ordinary.left(), shifted.left());
        assertEquals(ordinary.right(), shifted.right());
        assertEquals(ordinary.top() - 10, shifted.top());
        assertEquals(ordinary.bottom() - 10, shifted.bottom());
    }
}
