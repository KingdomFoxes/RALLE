package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatSystemIndicatorsTest {
    @Test
    void enabledSettingRemovesTagFromRenderViewOnly() {
        var content = Component.literal("System message");
        var original = new GuiMessage(42, content, null, GuiMessageTag.system());

        var renderView = ChatSystemIndicators.withoutIndicator(original, true);

        assertEquals(42, renderView.addedTime());
        assertSame(content, renderView.content());
        assertNull(renderView.tag());
        assertSame(GuiMessageTag.system(), original.tag());
    }

    @Test
    void disabledSettingReturnsOriginalMessage() {
        var original = new GuiMessage(42, Component.literal("System message"), null, GuiMessageTag.system());

        assertSame(original, ChatSystemIndicators.withoutIndicator(original, false));
    }

    @Test
    void untaggedMessagesDoNotAllocateAnotherRenderView() {
        var original = new GuiMessage(42, Component.literal("Player message"), null, null);

        assertSame(original, ChatSystemIndicators.withoutIndicator(original, true));
    }

    @Test
    void removingIndicatorRestoresVanillaShadowOnFirstVisibleRun() {
        var click = new ClickEvent.RunCommand("/first");
        var hover = new HoverEvent.ShowText(Component.literal("First run"));
        var firstStyle = Style.EMPTY.withColor(0xABCDEF).withoutShadow()
                .withClickEvent(click)
                .withHoverEvent(hover)
                .withInsertion("first");
        var secondStyle = Style.EMPTY.withColor(0x123456).withoutShadow();
        var content = Component.empty()
                .append(Component.literal("<").withStyle(firstStyle))
                .append(Component.literal("Player").withStyle(secondStyle));
        var original = new GuiMessage(42, content, null, GuiMessageTag.system());

        var renderView = ChatSystemIndicators.withoutIndicator(original, true);
        var runs = renderView.content().toFlatList();

        assertEquals(2, runs.size());
        assertEquals("<", runs.getFirst().getString());
        assertEquals(0xFF2A333B, runs.getFirst().getStyle().getShadowColor());
        assertSame(click, runs.getFirst().getStyle().getClickEvent());
        assertSame(hover, runs.getFirst().getStyle().getHoverEvent());
        assertEquals("first", runs.getFirst().getStyle().getInsertion());
        assertEquals("Player", runs.get(1).getString());
        assertEquals(Style.NO_SHADOW, runs.get(1).getStyle().getShadowColor());
    }

    @Test
    void removingIndicatorPreservesExistingFirstRunShadow() {
        var content = Component.literal("System message").withStyle(Style.EMPTY.withShadowColor(0xFF123456));
        var original = new GuiMessage(42, content, null, GuiMessageTag.system());

        var renderView = ChatSystemIndicators.withoutIndicator(original, true);

        assertSame(content, renderView.content());
        assertEquals(0xFF123456, renderView.content().getStyle().getShadowColor());
    }
}
