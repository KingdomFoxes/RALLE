package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTimestampsTest {
    @Test
    void formatsLeadingZeroesAndWynntilsColors() {
        var prefix = ChatTimestamps.prefix(LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        assertEquals("[03:04:05] ", prefix.getString());
        assertEquals(ChatFormatting.DARK_GRAY.getColor(),
                prefix.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(),
                prefix.getSiblings().get(1).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.DARK_GRAY.getColor(),
                prefix.getSiblings().get(2).getStyle().getColor().getValue());
    }

    @Test
    void prependingDoesNotFlattenInteractiveContent() {
        var click = new ClickEvent.RunCommand("/ralle settings");
        var interactiveStyle = Style.EMPTY.withClickEvent(click);
        var composed = ChatTimestamps.prepend(
                FormattedCharSequence.forward("[03:04:05] ", Style.EMPTY),
                FormattedCharSequence.forward("Open", interactiveStyle)
        );
        var observed = new StringBuilder();
        var clickEvents = new java.util.ArrayList<ClickEvent>();

        composed.accept((index, style, codePoint) -> {
            observed.appendCodePoint(codePoint);
            if (style.getClickEvent() != null) clickEvents.add(style.getClickEvent());
            return true;
        });

        assertEquals("[03:04:05] Open", observed.toString());
        assertEquals(java.util.List.of(click, click, click, click), clickEvents);
    }
}
