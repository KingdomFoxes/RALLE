package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiquidComponentTintTest {
    @Test void legacyColorsNeverBecomeExtraLettersAndResetKeepsTheInheritedFontAndHover() {
        var font = CosmeticTestFont.create();
        var resource = new FontDescription.Resource(Identifier.fromNamespaceAndPath("example", "icons"));
        var hover = new HoverEvent.ShowText(Component.literal("Player"));
        var inherited = Style.EMPTY.withFont(resource).withHoverEvent(hover);
        var original = Component.literal("§fPlayer§lX§r\ue001😀").withStyle(inherited);
        var tinted = LiquidComponentTint.apply(original, font, (x, y) -> 0xffffffff);

        assertEquals("PlayerX\ue001😀", tinted.getString());
        assertEquals(font.width(original), font.width(tinted));
        List<Style> styles = new ArrayList<>();
        tinted.getVisualOrderText().accept((index, style, codepoint) -> { styles.add(style); return true; });
        assertEquals(9, styles.size()); // Supplementary emoji remains one glyph.
        assertTrue(styles.get(6).isBold());
        assertFalse(styles.get(7).isBold());
        for (Style style : styles) {
            assertEquals(resource, style.getFont());
            assertSame(hover, style.getHoverEvent());
            assertEquals(0xffffff, style.getColor().getValue());
        }
    }

    @Test void originalComponentsStayUntouchedAndMaterialCursorSkipsFormattingCodes() {
        var font = CosmeticTestFont.create();
        var original = Component.literal("§aA§fB");
        List<Integer> positions = new ArrayList<>();
        var tinted = LiquidComponentTint.apply(original, font, (x, y) -> {
            positions.add(x);
            return x == 0 ? 0xffff0000 : 0xff00ff00;
        });
        assertEquals("§aA§fB", original.getString());
        assertEquals("AB", tinted.getString());
        assertEquals(List.of(0, 6), positions);
        assertEquals(0xff0000, tinted.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(0x00ff00, tinted.getSiblings().getLast().getStyle().getColor().getValue());
    }
}
