package org.kingdomfoxes.ralle;

import net.fabricmc.api.ClientModInitializer;
import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;

public final class RalleClient implements ClientModInitializer {
    public static final String MOD_ID = "ralle";

    private static RalleContext context;

    @Override
    public void onInitializeClient() {
        var features = new FeatureRegistry();
        var settings = new SettingsRegistry();

        // Future vertical slices register here. The foundation intentionally
        // registers no hooks, commands, keybinds, settings, or network clients.
        features.seal();
        settings.seal();

        context = new RalleContext(features, settings, new OwoSettingsScreenFactory(settings));
    }

    public static RalleContext context() {
        if (context == null) {
            throw new IllegalStateException("RALLE has not finished client initialization");
        }
        return context;
    }
}
