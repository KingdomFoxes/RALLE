package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleChatMessagesTest {
    @Test
    void notificationHasBrandedPrefixAndPreservesItsBody() {
        var body = Component.literal("Connection restored.");

        var message = RalleChatMessages.notification(body);

        assertEquals("RALLE: Connection restored.", message.getString());
        var prefix = message.getSiblings().getFirst();
        assertEquals(0xF2B84B, prefix.getStyle().getColor().getValue());
        assertTrue(prefix.getStyle().isBold());
        assertEquals(body, message.getSiblings().getLast());
    }

    @Test
    void clickableTextIsBoldUnderlinedGoldAndKeepsItsAction() {
        var interactive = RalleChatMessages.clickable(
                "FoxHost has pinged you!",
                new ClickEvent.RunCommand("/ralle lfg")
        );
        var style = interactive.getStyle();

        assertEquals(0xFFC83D, style.getColor().getValue());
        assertTrue(style.isBold());
        assertTrue(style.isUnderlined());
        assertEquals("/ralle lfg", assertInstanceOf(ClickEvent.RunCommand.class, style.getClickEvent()).command());
    }
}
