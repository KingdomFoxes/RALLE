package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatMessageProjectorTest {
    @Test
    void compactsNonConsecutiveDuplicatesInsideTheRollingWindow() {
        var messages = List.of(
                message(1_600, Component.literal("Repeated")),
                message(1_200, Component.literal("Between")),
                message(800, Component.literal("Repeated")),
                message(0, Component.literal("Repeated"))
        );

        var projected = ChatMessageProjector.project(messages, true, false, 900);

        assertEquals(2, projected.size());
        assertEquals("Repeated (3)", projected.get(0).content().getString());
        assertEquals(1_600, projected.get(0).addedTime());
        assertEquals("Between", projected.get(1).content().getString());
    }

    @Test
    void startsANewCountAfterTheWindowExpires() {
        var projected = ChatMessageProjector.project(
                List.of(
                        message(901, Component.literal("Repeated")),
                        message(0, Component.literal("Repeated"))
                ),
                true,
                false,
                900
        );

        assertEquals(2, projected.size());
        assertEquals("Repeated", projected.get(0).content().getString());
        assertEquals("Repeated", projected.get(1).content().getString());
    }

    @Test
    void formattingAndInteractiveMetadataRemainPartOfDuplicateEquality() {
        var hover = new HoverEvent.ShowText(Component.literal("Details"));
        var red = Component.literal("Same text").withStyle(ChatFormatting.RED);
        var blue = Component.literal("Same text").withStyle(ChatFormatting.BLUE);
        var interactive = Component.literal("Same text").withStyle(style -> style.withHoverEvent(hover));

        var projected = ChatMessageProjector.project(
                List.of(message(2, interactive), message(1, blue), message(0, red)),
                true,
                false,
                900
        );

        assertEquals(3, projected.size());
    }

    @Test
    void messageTagsRemainPartOfDuplicateEquality() {
        var firstTag = new GuiMessageTag(0xFFFFFF, null, Component.literal("First"), "First");
        var secondTag = new GuiMessageTag(0xFFFFFF, null, Component.literal("Second"), "Second");

        var projected = ChatMessageProjector.project(
                List.of(
                        new GuiMessage(1, Component.literal("Same"), null, secondTag),
                        new GuiMessage(0, Component.literal("Same"), null, firstTag)
                ),
                true,
                false,
                900
        );

        assertEquals(2, projected.size());
    }

    @Test
    void collapsesOnlyConsecutiveWhitespaceLinesWithoutAddingACounter() {
        var projected = ChatMessageProjector.project(
                List.of(
                        message(3, Component.literal("   ")),
                        message(2, Component.empty()),
                        message(1, Component.literal("Visible")),
                        message(0, Component.empty())
                ),
                true,
                true,
                900
        );

        assertEquals(3, projected.size());
        assertEquals("   ", projected.get(0).content().getString());
        assertEquals("Visible", projected.get(1).content().getString());
        assertEquals("", projected.get(2).content().getString());
    }

    @Test
    void repetitionCounterIsMutedAndNonInteractive() {
        var hover = new HoverEvent.ShowText(Component.literal("Original hover"));
        var original = Component.literal("Repeated").withStyle(style -> style.withHoverEvent(hover));
        var projected = ChatMessageProjector.project(
                List.of(message(1, original.copy()), message(0, original)),
                true,
                false,
                900
        );

        var displayed = projected.getFirst().content();
        var counter = displayed.getSiblings().get(1);
        assertEquals(ChatFormatting.GRAY.getColor(), counter.getStyle().getColor().getValue());
        assertNull(counter.getStyle().getHoverEvent());
        assertNull(counter.getStyle().getClickEvent());
        assertEquals(hover, displayed.getSiblings().getFirst().getStyle().getHoverEvent());
    }

    private GuiMessage message(int addedTime, Component component) {
        return new GuiMessage(addedTime, component, null, null);
    }
}
