package org.kingdomfoxes.ralle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import org.kingdomfoxes.ralle.sound.MinecraftChatSelectionSoundPlayer;
import org.kingdomfoxes.ralle.sound.RalleSoundEvents;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;
import org.kingdomfoxes.ralle.ui.owo.RaidLfgScreen;
import org.kingdomfoxes.ralle.ui.owo.RalleTypography;
import org.kingdomfoxes.ralle.ui.owo.SettingsNavigationState;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgKeybind;
import org.kingdomfoxes.ralle.lfg.client.HttpLfgGateway;
import org.kingdomfoxes.ralle.lfg.client.MinecraftRaidLfgEnvironment;
import org.kingdomfoxes.ralle.lfg.client.MinecraftSessionProofAdapter;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class RalleClient implements ClientModInitializer {
    public static final String MOD_ID = "ralle";

    private static RalleContext context;

    @Override
    public void onInitializeClient() {
        FullShadowRenderingStrategy.registerCompositor();
        RalleSoundEvents.register();

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
        RalleTypography.bind(settings);

        var chatLayout = new ChatLayoutService(Minecraft.getInstance(), settings, placements);
        var navigation = new SettingsNavigationState(configDirectory.resolve("ralle-settings-ui.properties"), settings);
        var minecraft = Minecraft.getInstance();
        var raidLfg = new RaidLfgService(
                new HttpLfgGateway(),
                new MinecraftRaidLfgEnvironment(minecraft, settings),
                new MinecraftSessionProofAdapter(minecraft)
        );
        var lfgKeybind = new RaidLfgKeybind(minecraft, settings, raidLfg);
        var chatBehavior = new ChatBehaviorService(Minecraft.getInstance(), settings);
        var chatScreenshots = new ChatScreenshotService(
                Minecraft.getInstance(),
                settings,
                new TransparentChatCapture(Minecraft.getInstance()),
                new MinecraftChatSelectionSoundPlayer(Minecraft.getInstance(), settings)
        );
        context = new RalleContext(
                features,
                settings,
                new OwoSettingsScreenFactory(settings, chatLayout, navigation),
                chatLayout,
                chatBehavior,
                chatScreenshots,
                raidLfg
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> raidLfg.connectionChanged());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> raidLfg.connectionChanged());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            chatLayout.tick();
            chatBehavior.tick();
            chatScreenshots.tick();
            lfgKeybind.tick();
            raidLfg.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("ralle").then(literal("settings").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(context().settingsScreens().create(client.screen)));
                    return 1;
                })).then(literal("lfg").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(new RaidLfgScreen(client.screen, context().raidLfg())));
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
