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
import org.kingdomfoxes.ralle.sound.MinecraftLfgSoundPlayer;
import org.kingdomfoxes.ralle.sound.RalleSoundEvents;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;
import org.kingdomfoxes.ralle.ui.owo.LfgNotificationOverlay;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarOverlay;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarState;
import org.kingdomfoxes.ralle.ui.owo.RaidLfgScreen;
import org.kingdomfoxes.ralle.ui.owo.RalleTypography;
import org.kingdomfoxes.ralle.ui.owo.SettingsNavigationState;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgKeybinds;
import org.kingdomfoxes.ralle.lfg.client.LfgDisbandConfirmation;
import org.kingdomfoxes.ralle.lfg.client.MinecraftRaidRegionDetector;
import org.kingdomfoxes.ralle.lfg.client.HttpLfgGateway;
import org.kingdomfoxes.ralle.lfg.client.MinecraftRaidLfgEnvironment;
import org.kingdomfoxes.ralle.lfg.client.MinecraftSessionProofAdapter;
import org.kingdomfoxes.ralle.lfg.client.MinecraftLfgNotificationSink;
import org.kingdomfoxes.ralle.lfg.client.MinecraftHostPartyInviteSink;
import org.kingdomfoxes.ralle.lfg.client.MinecraftPartyCommandExecutor;
import org.kingdomfoxes.ralle.lfg.client.HostPartyInviteController;
import org.kingdomfoxes.ralle.lfg.client.LfgKeybindHints;
import org.kingdomfoxes.ralle.lfg.client.LfgLockDebouncer;
import org.kingdomfoxes.ralle.lfg.client.LfgNotificationManager;
import org.kingdomfoxes.ralle.lfg.client.LfgRosterSoundController;
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
        placements.register(new HudPlacementRegistry.ElementDefinition(
                LfgNotificationOverlay.ELEMENT_ID,
                LfgNotificationOverlay.CARD_WIDTH,
                LfgNotificationOverlay.CARD_HEIGHT,
                HudPlacementRegistry.PlacementPolicy.FIXED_SIDE_ANCHORED
        ));
        features.seal();
        settings.seal();
        placements.seal();
        RalleTypography.bind(settings);

        var chatLayout = new ChatLayoutService(Minecraft.getInstance(), placements);
        var navigation = new SettingsNavigationState(configDirectory.resolve("ralle-settings-ui.properties"), settings);
        var minecraft = Minecraft.getInstance();
        var regionDetector = new MinecraftRaidRegionDetector(minecraft);
        var lfgSounds = new MinecraftLfgSoundPlayer(minecraft, settings);
        var partyCommands = new MinecraftPartyCommandExecutor(minecraft);
        var raidLfg = new RaidLfgService(
                new HttpLfgGateway(),
                new MinecraftRaidLfgEnvironment(minecraft, settings),
                new MinecraftSessionProofAdapter(minecraft),
                new MinecraftLfgNotificationSink(minecraft, lfgSounds),
                partyCommands
        );
        var lfgNotifications = new LfgNotificationManager(
                raidLfg, settings, lfgSounds, () -> minecraft.screen instanceof RaidLfgScreen);
        new LfgRosterSoundController(raidLfg.store(), lfgSounds);
        var hostPartyInvites = new HostPartyInviteController(
                raidLfg,
                partyCommands,
                new MinecraftHostPartyInviteSink(minecraft),
                lfgNotifications::hasPersistentCard
        );
        var disbandConfirmation = new LfgDisbandConfirmation();
        var lockDebouncer = new LfgLockDebouncer();
        var keybindHints = new LfgKeybindHints(settings);
        var lfgNotificationOverlay = new LfgNotificationOverlay(
                minecraft, raidLfg, lfgNotifications, hostPartyInvites, placements, lfgSounds,
                disbandConfirmation, keybindHints);
        lfgNotificationOverlay.register();
        var actionBarState = new LfgActionBarState();
        var actionBarOverlay = new LfgActionBarOverlay(minecraft, actionBarState);
        actionBarOverlay.register();
        var lfgKeybinds = new RaidLfgKeybinds(
                minecraft, settings, raidLfg, lfgSounds, lfgNotifications, hostPartyInvites, regionDetector,
                actionBarState, disbandConfirmation, lockDebouncer);
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
                raidLfg,
                lfgKeybinds,
                hostPartyInvites,
                lfgSounds
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> raidLfg.connectionChanged());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> raidLfg.connectionChanged());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            chatLayout.tick();
            chatBehavior.tick();
            chatScreenshots.tick();
            raidLfg.tick();
            lfgKeybinds.tick();
            hostPartyInvites.tick();
            lfgNotificationOverlay.tick();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("ralle").then(literal("settings").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(context().settingsScreens().create(client.screen)));
                    return 1;
                })).then(literal("lfg").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(new RaidLfgScreen(
                            client.screen, context().raidLfg(), regionDetector,
                            context().lfgSounds(), lfgNotifications, lockDebouncer)));
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

    public static boolean initialized() {
        return context != null;
    }
}
