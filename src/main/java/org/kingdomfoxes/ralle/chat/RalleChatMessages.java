package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotTokens;

/** Shared presentation and delivery path for RALLE-authored local chat notifications. */
public final class RalleChatMessages {
    private static final int PREFIX_GOLD = 0xF2B84B;
    private static final int INTERACTIVE_GOLD = ChatScreenshotTokens.SELECTION_GOLD & 0x00FFFFFF;
    private RalleChatMessages() {}

    public static void post(Minecraft minecraft, Component body) {
        minecraft.gui.getChat().addMessage(notification(body), null, null);
    }

    public static Component notification(Component body) {
        return Component.empty()
                .append(Component.literal("RALLE: ").withStyle(style -> style
                        .withColor(PREFIX_GOLD)
                        .withBold(true)))
                .append(body);
    }

    public static Component clickable(String text, ClickEvent clickEvent) {
        return Component.literal(text)
                .withStyle(style -> style
                        .withColor(INTERACTIVE_GOLD)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(clickEvent));
    }

}
