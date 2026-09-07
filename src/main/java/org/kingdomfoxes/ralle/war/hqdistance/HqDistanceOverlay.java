package org.kingdomfoxes.ralle.war.hqdistance;

import net.minecraft.client.gui.GuiGraphics;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runtime-safe entrypoint used by the optional Wynntils mixin. */
public final class HqDistanceOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(HqDistanceOverlay.class);
    private static BooleanSetting enabled;
    private static boolean supported;
    private static InspectionKeybind keybind;
    private static HqInspectionService service;
    private static boolean failed;

    private HqDistanceOverlay() {}

    public static void configure(BooleanSetting setting, boolean compatible) {
        enabled = setting;
        supported = compatible;
        failed = false;
    }

    public static void configureKeybind(org.kingdomfoxes.ralle.api.settings.KeybindSetting setting) {
        keybind = new InspectionKeybind(setting);
    }

    public static void render(
            Object territoryPoi,
            GuiGraphics graphics,
            float renderX,
            float renderY,
            boolean hovered,
            float zoomRenderScale
    ) {
        if (enabled == null || !enabled.value() || !supported || failed || !hovered || !inspectionHeld()) return;
        try {
            WynntilsHqDistanceIntegration.render(
                    territoryPoi, graphics, renderX, renderY, zoomRenderScale, inspectionService());
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            if (service != null) service.clear();
            LOGGER.error("Disabling HQ distance inspection for this session after a Wynntils integration failure", exception);
        }
    }

    public static void clear() {
        if (service != null) service.clear();
    }

    private static HqInspectionService inspectionService() {
        if (service == null) {
            service = new HqInspectionService(
                    new WynntilsTerritorySnapshotSource(),
                    new TerritoryRouteCalculator(),
                    new ProvisionalQueueDurationEstimator());
        }
        return service;
    }

    private static boolean inspectionHeld() {
        return keybind != null && keybind.held();
    }
}
