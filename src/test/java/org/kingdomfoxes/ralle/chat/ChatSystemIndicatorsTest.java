package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
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
}
