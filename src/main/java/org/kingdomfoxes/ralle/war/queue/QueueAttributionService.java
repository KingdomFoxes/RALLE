package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Runtime-safe facade used by Fabric lifecycle hooks and the optional Wynntils mixin. */
public final class QueueAttributionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueAttributionService.class);
    private static final QueueAttributionDemo DEVELOPMENT_DEMO = new QueueAttributionDemo(System::currentTimeMillis);
    private static BooleanSetting enabled;
    private static boolean supported;
    private static boolean failed;
    private static QueueAttributionAdapter integration;

    private QueueAttributionService() {}

    public static void configure(BooleanSetting setting, boolean compatible) {
        enabled = setting;
        supported = compatible;
        failed = false;
        DEVELOPMENT_DEMO.clear();
    }

    public static void tick(Minecraft minecraft) {
        if (!requested() || !connectedToWynncraft(minecraft)) {
            DEVELOPMENT_DEMO.clear();
            stop(false);
            return;
        }
        try {
            var adapter = integration();
            if (!adapter.ready()) {
                stop(false);
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
        DEVELOPMENT_DEMO.clear();
        stop(true);
        failed = false;
    }

    static void fail(Throwable exception) {
        if (failed) return;
        failed = true;
        stop(false);
        LOGGER.error("Disabling war queue attribution for this session after a Wynntils integration failure", exception);
    }

    public static DevelopmentPreviewState toggleDevelopmentPreview() {
        boolean available = active() && connectedToWynncraft(Minecraft.getInstance());
        return switch (DEVELOPMENT_DEMO.toggle(available)) {
            case ENABLED -> DevelopmentPreviewState.ENABLED;
            case DISABLED -> DevelopmentPreviewState.DISABLED;
            case UNAVAILABLE -> DevelopmentPreviewState.UNAVAILABLE;
        };
    }

    public static List<DevelopmentTimer> developmentTimers() {
        if (!active()) return List.of();
        return DEVELOPMENT_DEMO.timers().stream()
                .map(timer -> new DevelopmentTimer(timer.key(), timer.timerEndMillis()))
                .toList();
    }

    static java.util.Optional<QueueAttributionDemo.Row> developmentRow(String key) {
        return DEVELOPMENT_DEMO.row(key);
    }

    public enum DevelopmentPreviewState { ENABLED, DISABLED, UNAVAILABLE }
    public record DevelopmentTimer(String key, long timerEndMillis) {}

    private static boolean active() {
        return requested() && integration != null && integration.registered();
    }

    private static boolean requested() {
        return enabled != null && enabled.value() && supported && !failed;
    }

    private static boolean connectedToWynncraft(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return false;
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

    private static void stop(boolean discardAdapter) {
        if (integration == null) return;
        try {
            integration.unregister();
        } catch (RuntimeException | LinkageError exception) {
            if (!failed) LOGGER.warn("Could not unregister the Wynntils queue attribution listener", exception);
        }
        integration.clear();
        if (discardAdapter) integration = null;
    }
}
