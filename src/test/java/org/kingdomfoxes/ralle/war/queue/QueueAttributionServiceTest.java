package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QueueAttributionServiceTest {
    @Test
    void disconnectAndTemporaryMissingConnectionPauseButDisableClears() throws Exception {
        var enabled = new BooleanSetting("queue-attribution-enabled", Component.literal("Queue"), Component.empty());
        enabled.set(true);
        var adapter = new FakeAdapter();
        var field = QueueAttributionService.class.getDeclaredField("integration");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, adapter);
        try {
            QueueAttributionService.configure(enabled, true);
            QueueAttributionService.disconnect();
            assertFalse(adapter.registered());
            assertEquals(0, adapter.clears);
            QueueAttributionService.tick(null);
            assertEquals(0, adapter.clears);
            enabled.set(false);
            QueueAttributionService.tick(null);
            assertEquals(1, adapter.clears);
        } finally {
            field.set(null, previous);
            QueueAttributionService.configure(enabled, false);
        }
    }

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

    private static final class FakeAdapter implements QueueAttributionAdapter {
        private boolean registered = true;
        private int clears;
        public boolean ready() { return false; }
        public void register() { registered = true; }
        public void unregister() { registered = false; }
        public boolean registered() { return registered; }
        public void tick(net.minecraft.client.Minecraft minecraft) {}
        public void decorateTimer(Object timer, Object renderTask) {}
        public void decoratePreview(Object renderTask) {}
        public void clear() { clears++; }
    }
}
