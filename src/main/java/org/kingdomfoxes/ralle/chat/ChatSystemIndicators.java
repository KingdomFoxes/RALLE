package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;

/**
 * Produces an indicator-free render view while retaining the authoritative chat
 * message, including its signature and tag, in Minecraft's message history.
 */
public final class ChatSystemIndicators {
    private ChatSystemIndicators() {}

    public static GuiMessage withoutIndicator(GuiMessage message, boolean removeIndicator) {
        if (!removeIndicator || message.tag() == null) return message;
        return new GuiMessage(message.addedTime(), message.content(), message.signature(), null);
    }
}
