package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Runtime-safe facade used by Fabric lifecycle hooks and the optional Wynntils mixin. */
public final class QueueAttributionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueAttributionService.class);
    private static BooleanSetting enabled;
    private static org.kingdomfoxes.ralle.api.settings.ColorSetting selfColor;
    public static void configureColor(org.kingdomfoxes.ralle.api.settings.ColorSetting setting) { selfColor = setting; }
    static org.kingdomfoxes.ralle.war.consumables.HighlightStyle selfColor() {
        return selfColor == null ? new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x5555FF, false) : selfColor.value();
    }
    private static boolean supported;
    private static boolean failed;
    private static QueueAttributionAdapter integration;

    private QueueAttributionService() {}

    public static void configure(BooleanSetting setting, boolean compatible) {
        enabled = setting;
        supported = compatible;
        failed = false;
    }

    public static void tick(Minecraft minecraft) {
        if (!requested()) {
            stop();
            return;
        }
        if (!connectedToWynncraft(minecraft)) {
            pause();
            return;
        }
        try {
            var adapter = integration();
            if (minecraft.level == null || minecraft.player == null || !adapter.ready()) {
                // Wynncraft world/character loading is a pause, not a new attribution session.
                adapter.unregister();
                return;
            }
            adapter.register();
            adapter.tick(minecraft);
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
        }
    }

    public static void decorateTimer(Object timer, Object renderTask) {
        if (!active()) return;
        try {
            integration.decorateTimer(timer, renderTask);
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
        }
    }

    public static Object decoratePreview(Object renderTask) {
        if (!active()) return renderTask;
        try {
            integration.decoratePreview(renderTask);
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
        }
        return renderTask;
    }

    public static void disconnect() {
        // Keep bounded, expiring names for reconnects to the same account/guild.
        pause();
        failed = false;
    }

    static void fail(Throwable exception) {
        if (failed) return;
        failed = true;
        stop();
        LOGGER.error("Disabling war queue attribution for this session after a Wynntils integration failure", exception);
    }

    private static boolean active() {
        return requested() && integration != null && integration.registered();
    }

    private static boolean requested() {
        return enabled != null && enabled.value() && supported && !failed;
    }

    private static boolean connectedToWynncraft(Minecraft minecraft) {
        if (minecraft == null) return false;
        var server = minecraft.getCurrentServer();
        return server != null && WynncraftHost.matches(server.ip);
    }

    private static QueueAttributionAdapter integration() {
        if (integration == null) {
            integration = new WynntilsQueueAttributionIntegration(
                    new QueueAnnouncementParser(),
                    new QueueAttributionTracker(System::currentTimeMillis));
        }
        return integration;
    }

    private static void pause() {
        if (integration == null) return;
        try {
            integration.unregister();
        } catch (RuntimeException | LinkageError exception) {
            if (!failed) LOGGER.warn("Could not unregister the Wynntils queue attribution listener", exception);
        }
    }

    private static void stop() {
        pause();
        if (integration == null) return;
        integration.clear();
    }
}
