package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleChatMessagesTest {
    @Test
    void exampleHasPrefixThreeLinesAndInteractiveGoldText() {
        var message = RalleChatMessages.example();

        assertTrue(message.getString().startsWith("RALLE: "));
        assertEquals(3, message.getString().lines().count());

        var interactive = message.getSiblings().stream()
                .filter(component -> component.getString().equals("dolor sit amet"))
                .findFirst()
                .orElseThrow();
        var style = interactive.getStyle();
        assertEquals(0xFFC83D, style.getColor().getValue());
        assertTrue(style.isBold());
        assertTrue(style.isUnderlined());
        assertEquals("/ralle settings", assertInstanceOf(ClickEvent.RunCommand.class, style.getClickEvent()).command());
        assertEquals(
                "Open RALLE settings",
                assertInstanceOf(HoverEvent.ShowText.class, style.getHoverEvent()).value().getString()
        );
    }
}
