package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;

import static org.junit.jupiter.api.Assertions.assertSame;

class QueueAttributionServiceTest {
    @Test
    void unsupportedRuntimeDoesNotResolveOrLoadWynntilsClasses() {
        var enabled = new BooleanSetting("queue-attribution-enabled", Component.literal("Queue"), Component.empty());
        enabled.set(true);

        QueueAttributionService.configure(enabled, false);
        QueueAttributionService.tick(null);
        Object untouched = new Object();
        assertSame(untouched, QueueAttributionService.decoratePreview(untouched));
        QueueAttributionService.disconnect();
    }
}
