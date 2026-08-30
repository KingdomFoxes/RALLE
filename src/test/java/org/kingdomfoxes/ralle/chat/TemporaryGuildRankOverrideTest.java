package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TemporaryGuildRankOverrideTest {
    private static final int ZERO = 0xD0000;

    @Test
    void replacesBothStrategistPassesAndForcesTheIndicatorAndBackgroundToGuildAqua() {
        var indicator = Component.literal(strategistIndicator());
        var background = Component.literal(background("STRATEGIST"));
        var foreground = Component.literal(foreground("STRATEGIST")).withStyle(ChatFormatting.BLACK);
        var original = Component.empty()
                .append(indicator)
                .append(Component.literal(" "))
                .append(background)
                .append(foreground)
                .append(Component.literal(" player: hello").withStyle(ChatFormatting.AQUA));

        var transformed = TemporaryGuildRankOverride.apply(original);

        assertEquals(
                strategistIndicator() + " "
                        + background("KING CRICKET") + foreground("KING CRICKET") + " player: hello",
                transformed.getString()
        );
        assertEquals(ChatFormatting.AQUA.getColor(), transformed.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.AQUA.getColor(), transformed.getSiblings().get(2).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.BLACK.getColor(), transformed.getSiblings().get(3).getStyle().getColor().getValue());
    }

    @Test
    void leavesUnrelatedMessagesUntouched() {
        var original = Component.literal("ordinary chat message");

        assertSame(original, TemporaryGuildRankOverride.apply(original));
    }

    private static String background(String title) {
        int width = 0;
        var result = new StringBuilder().appendCodePoint(0xE060).appendCodePoint(ZERO - 1);
        for (char character : title.toCharArray()) {
            result.appendCodePoint(character == ' ' ? 0xE061 : 0xE030 + character - 'A');
            result.appendCodePoint(ZERO - 1);
            width += character == 'I' || character == ' ' ? 4 : 6;
        }
        return result.appendCodePoint(0xE062).appendCodePoint(ZERO - (width + 2)).toString();
    }

    private static String strategistIndicator() {
        return new StringBuilder()
                .appendCodePoint(0xCFFFC)
                .appendCodePoint(0xE006)
                .appendCodePoint(0xCFFFF)
                .appendCodePoint(0xE002)
                .appendCodePoint(0xCFFFE)
                .toString();
    }

    private static String foreground(String title) {
        var result = new StringBuilder();
        for (char character : title.toCharArray()) {
            if (character == ' ') result.append(' ');
            else result.appendCodePoint(0xE000 + character - 'A');
        }
        return result.appendCodePoint(ZERO + 2).toString();
    }
}
