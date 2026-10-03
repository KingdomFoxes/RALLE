package org.kingdomfoxes.ralle.update;

import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpdateMessagesTest {
    @Test
    void notificationPreservesCopyVersionNameAndClickableChangelog() {
        var release = new ModrinthRelease("ABCDEFGH", "RALLE 0.1.9", "0.1.9");
        var message = RalleChatMessages.notification(UpdateMessages.body(release));
        assertEquals("RALLE: A new version has released! RALLE 0.1.9.", message.getString());
        var links = new ArrayList<ClickEvent.OpenUrl>();
        message.visit((style, text) -> {
            if (style.getClickEvent() instanceof ClickEvent.OpenUrl link) {
                assertEquals("RALLE 0.1.9", text);
                assertTrue(style.isBold());
                assertTrue(style.isUnderlined());
                assertEquals(org.kingdomfoxes.ralle.ui.theme.RallePalette.accent(), style.getColor().getValue());
                links.add(link);
            }
            return Optional.<Object>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        assertEquals(1, links.size());
        assertEquals(release.changelogUrl(), links.getFirst().uri());
    }
}
