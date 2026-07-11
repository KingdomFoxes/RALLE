package org.kingdomfoxes.ralle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class RalleClient implements ClientModInitializer {
    public static final String MOD_ID = "ralle";

    private static RalleContext context;

    @Override
    public void onInitializeClient() {
        var features = new FeatureRegistry();
        var settings = new SettingsRegistry(FabricLoader.getInstance().getConfigDir().resolve("ralle.properties"));

        RalleSettings.register(settings);
        features.seal();
        settings.seal();

        context = new RalleContext(features, settings, new OwoSettingsScreenFactory(settings));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("ralle").then(literal("settings").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(context().settingsScreens().create(client.screen)));
                    return 1;
                }))
        ));
    }

    public static RalleContext context() {
        if (context == null) {
            throw new IllegalStateException("RALLE has not finished client initialization");
        }
        return context;
    }
}
