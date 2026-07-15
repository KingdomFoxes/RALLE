package org.kingdomfoxes.ralle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.render.FullShadowRenderingStrategy;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService;
import org.kingdomfoxes.ralle.chat.screenshot.TransparentChatCapture;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;
import org.kingdomfoxes.ralle.ui.owo.RaidLfgScreen;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class RalleClient implements ClientModInitializer {
    public static final String MOD_ID = "ralle";

    private static RalleContext context;

    @Override
    public void onInitializeClient() {
        FullShadowRenderingStrategy.registerCompositor();

        var features = new FeatureRegistry();
        var configDirectory = FabricLoader.getInstance().getConfigDir();
        var settings = new SettingsRegistry(configDirectory.resolve("ralle.properties"));
        var placements = new HudPlacementRegistry(configDirectory.resolve("ralle-hud-layout.properties"));

        RalleSettings.register(settings);
        placements.register(new HudPlacementRegistry.ElementDefinition(
                ChatLayoutService.CHAT_ELEMENT_ID,
                ChatLayoutService.MINIMUM_WIDTH,
                ChatLayoutService.MINIMUM_HEIGHT
        ));
        features.seal();
        settings.seal();
        placements.seal();

        var chatLayout = new ChatLayoutService(Minecraft.getInstance(), settings, placements);
        var chatBehavior = new ChatBehaviorService(Minecraft.getInstance(), settings);
        var chatScreenshots = new ChatScreenshotService(
                Minecraft.getInstance(),
                settings,
                new TransparentChatCapture(Minecraft.getInstance())
        );
        context = new RalleContext(
                features,
                settings,
                new OwoSettingsScreenFactory(settings, chatLayout),
                chatLayout,
                chatBehavior,
                chatScreenshots
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            chatLayout.tick();
            chatBehavior.tick();
            chatScreenshots.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("ralle").then(literal("settings").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(context().settingsScreens().create(client.screen)));
                    return 1;
                })).then(literal("lfg").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(new RaidLfgScreen(client.screen)));
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
