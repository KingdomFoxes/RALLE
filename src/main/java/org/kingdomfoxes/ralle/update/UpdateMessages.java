package org.kingdomfoxes.ralle.update;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;

/** Uses the shared notification prefix and clickable-span styling. */
public final class UpdateMessages {
    private UpdateMessages() {}

    public static Component body(ModrinthRelease release) {
        return Component.translatableWithFallback("ralle.update.available", "A new version has released! %s.",
                RalleChatMessages.clickable(release.name(), new ClickEvent.OpenUrl(release.changelogUrl())));
    }
}
