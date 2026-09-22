package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotTokens;

/** Shared presentation and delivery path for RALLE-authored local chat notifications. */
public final class RalleChatMessages {
    private RalleChatMessages() {}

    public static void post(Minecraft minecraft, Component body) {
        minecraft.gui.getChat().addMessage(notification(body), null, null);
    }

    public static Component notification(Component body) {
        return Component.empty()
                .append(Component.literal("RALLE: ").withStyle(style -> style
                        .withColor(org.kingdomfoxes.ralle.ui.theme.RallePalette.accent())
                        .withBold(true)))
                .append(body);
    }

    public static Component clickable(String text, ClickEvent clickEvent) {
        return Component.literal(text)
                .withStyle(style -> style
                        .withColor(org.kingdomfoxes.ralle.ui.theme.RallePalette.accent())
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(clickEvent));
    }

}
