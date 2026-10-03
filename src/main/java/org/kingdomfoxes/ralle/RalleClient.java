package org.kingdomfoxes.ralle;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.cosmetics.*;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;
import org.kingdomfoxes.ralle.chat.RalleOnboardingNotice;
import org.kingdomfoxes.ralle.chat.input.ChatTypeTabService;
import org.kingdomfoxes.ralle.chat.input.WynncraftChatInputController;
import org.kingdomfoxes.ralle.chat.identity.DirectMessageIdentityResolver;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.kingdomfoxes.ralle.update.ModrinthUpdateLookup;
import org.kingdomfoxes.ralle.update.UpdateMessages;
import org.kingdomfoxes.ralle.update.UpdateNotice;
import org.kingdomfoxes.ralle.chat.rank.GuildRankService;
import org.kingdomfoxes.ralle.chat.rank.HttpGuildRankGateway;
import org.kingdomfoxes.ralle.chat.render.FullShadowRenderingStrategy;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService;
import org.kingdomfoxes.ralle.chat.screenshot.TransparentChatCapture;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.sound.MinecraftChatSelectionSoundPlayer;
import org.kingdomfoxes.ralle.sound.MinecraftLfgSoundPlayer;
import org.kingdomfoxes.ralle.sound.RalleSoundEvents;
import org.kingdomfoxes.ralle.ui.owo.OwoSettingsScreenFactory;
import org.kingdomfoxes.ralle.ui.owo.ChatLayoutEditorScreen;
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
import org.kingdomfoxes.ralle.requeue.AutoRaidRequeueController;
import org.kingdomfoxes.ralle.requeue.AutoRaidRequeueStore;
import org.kingdomfoxes.ralle.requeue.RaidRequeueMessages;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightService;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;
import org.kingdomfoxes.ralle.war.hqdistance.HqDistanceOverlay;
import org.kingdomfoxes.ralle.war.hqdistance.WynntilsCompatibility;
import org.kingdomfoxes.ralle.war.queue.QueueAttributionService;
import org.kingdomfoxes.ralle.diagnostics.DiagnosticProfiler;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class RalleClient implements ClientModInitializer {
    public static final String MOD_ID = "ralle";

    private static RalleContext context;

    @Override
    public void onInitializeClient() {
        // -PlocalBackend also marks packaged jars; an explicit JVM setting takes precedence.
        if (System.getProperty("ralle.localBackend") == null
                && RalleClient.class.getResource("/ralle-local-backend") != null) {
            System.setProperty("ralle.localBackend", "true");
        }
        FullShadowRenderingStrategy.registerCompositor();
        RalleSoundEvents.register();

        var configDirectory = FabricLoader.getInstance().getConfigDir();
        boolean existingInstall = RalleOnboardingNotice.hasExistingConfig(configDirectory);
        var settings = new SettingsRegistry(configDirectory.resolve("ralle.properties"));
        var placements = new HudPlacementRegistry(configDirectory.resolve("ralle-hud-layout.properties"));
        var onboarding = new RalleOnboardingNotice(
                configDirectory.resolve("ralle-onboarding.properties"), existingInstall);

        RalleSettings.register(settings);
        var foxGuildAccess = new org.kingdomfoxes.ralle.client.FoxGuildAccess(
                new org.kingdomfoxes.ralle.client.HttpFoxGuildLookup()::lookup, System::currentTimeMillis);
        RalleSettings.requireFoxAccess(settings, foxGuildAccess::allowed);
        var minecraft = Minecraft.getInstance();
        var updateLookup = new ModrinthUpdateLookup(
                FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                        .getMetadata().getVersion().getFriendlyString(),
                FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                        .getMetadata().getVersion().getFriendlyString());
        var updateNotice = new UpdateNotice(updateLookup::lookup, () -> System.nanoTime() / 1_000_000L);
        var cosmeticDirectory = new NameplateDirectorySession(
                new HttpNameplateDirectory(), () -> true,
                () -> minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().ip,
                () -> minecraft.getUser().getProfileId(), java.time.Clock.systemUTC());
        RalleSettings.requireSupporterAccess(settings, cosmeticDirectory::settingsAllowed);
        var wynntilsCompatibility = WynntilsCompatibility.detect();
        if (!wynntilsCompatibility.supported()) {
            settings.markUnavailable(
                    RalleSettings.HQ_DISTANCE_ENABLED_ID,
                    net.minecraft.network.chat.Component.translatable(wynntilsCompatibility
                            == WynntilsCompatibility.Status.MISSING
                            ? "ralle.settings.hq-distance.missing-wynntils"
                            : "ralle.settings.hq-distance.unsupported-wynntils"));
            settings.markUnavailable(
                    RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID,
                    net.minecraft.network.chat.Component.translatable(wynntilsCompatibility
                            == WynntilsCompatibility.Status.MISSING
                            ? "ralle.settings.queue-attribution.missing-wynntils"
                            : "ralle.settings.queue-attribution.unsupported-wynntils"));
        }
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
        settings.seal();
        placements.seal();
        RalleTypography.bind(settings);
        org.kingdomfoxes.ralle.ui.theme.RallePalette.bind(settings);
        RalleTypography.registerResourceInvalidation();
        HqDistanceOverlay.configureKeybind(settings.setting(RalleSettings.HQ_DISTANCE_KEYBIND_ID, KeybindSetting.class));
        QueueAttributionService.configureColor(settings.setting(RalleSettings.QUEUE_SELF_COLOR_ID,
                org.kingdomfoxes.ralle.api.settings.ColorSetting.class));
        HqDistanceOverlay.configure(
                settings.setting(RalleSettings.HQ_DISTANCE_ENABLED_ID, BooleanSetting.class),
                wynntilsCompatibility.supported());
        QueueAttributionService.configure(
                settings.setting(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID, BooleanSetting.class),
                wynntilsCompatibility.supported());

        var chatLayout = new ChatLayoutService(Minecraft.getInstance(), placements);
        var navigation = new SettingsNavigationState(configDirectory.resolve("ralle-settings-ui.properties"), settings);
        var cosmeticStyleSelection = new CosmeticStyleSelection(
                new HttpCosmeticSelfGateway(), new MinecraftSessionProofAdapter(minecraft),
                cosmeticDirectory, () -> true,
                () -> minecraft.getUser().getProfileId(), () -> minecraft.getUser().getName());
        var cosmeticTextures = new LiquidMaterialTextures(minecraft);
        var cosmeticReloadPending = new java.util.concurrent.atomic.AtomicBoolean();
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloader(net.minecraft.resources.Identifier.fromNamespaceAndPath("ralle", "cosmetic_textures"),
                        (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                                cosmeticReloadPending.set(true));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cosmeticTextures.close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                org.kingdomfoxes.ralle.platform.SharedHttpTransport.shared().close());
        var consumableHighlights = new ConsumableHighlightService(
                minecraft,
                settings.setting(RalleSettings.CONSUMABLE_HIGHLIGHTS_ENABLED_ID, BooleanSetting.class),
                new ConsumableHighlightStore(configDirectory.resolve("ralle-consumable-highlights.json")),
                System::currentTimeMillis
        );
        var guildRanks = new GuildRankService(
                new HttpGuildRankGateway(new org.kingdomfoxes.ralle.chat.rank.GuildRankCredentials(
                        new HttpLfgGateway(), new MinecraftSessionProofAdapter(minecraft),
                        () -> minecraft.getUser().getProfileId(), () -> minecraft.getUser().getName(),
                        () -> (settings.setting(RalleSettings.INTERNAL_GUILD_RANKS_ID, BooleanSetting.class).value()
                                || QueueAttributionService.rankColorsRequested())
                                && org.kingdomfoxes.ralle.client.WynncraftHost.matches(
                                        minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().ip))),
                configDirectory.resolve("ralle-ranks.json"),
                settings.setting(RalleSettings.INTERNAL_GUILD_RANKS_ID, BooleanSetting.class),
                settings.setting(RalleSettings.GUILD_RANK_STYLE_ID, ChoiceSetting.class),
                () -> minecraft.getCurrentServer() == null ? "" : minecraft.getCurrentServer().ip,
                System::currentTimeMillis,
                QueueAttributionService::rankColorsRequested
        );
        QueueAttributionService.configureRankColors(
                settings.setting(RalleSettings.QUEUE_KOF_RANK_COLORS_ID, BooleanSetting.class),
                guildRanks::titleFor);
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
        var autoRaidRequeue = new AutoRaidRequeueController(
                minecraft,
                settings.setting(AutoRaidRequeueController.KEYBIND_ID, KeybindSetting.class),
                new AutoRaidRequeueStore(configDirectory.resolve("ralle-auto-requeue.properties")),
                actionBarState
        );
        var lfgKeybinds = new RaidLfgKeybinds(
                minecraft, settings, raidLfg, lfgSounds, lfgNotifications, hostPartyInvites, regionDetector,
                actionBarState, disbandConfirmation, lockDebouncer, autoRaidRequeue);
        var chatBehavior = new ChatBehaviorService(Minecraft.getInstance(), settings);
        chatBehavior.registerResourceInvalidation();
        var chatTypeTabs = new ChatTypeTabService();
        var chatInput = new WynncraftChatInputController(Minecraft.getInstance(),
                () -> settings.setting(ChatTypeTabService.SETTING_ID, BooleanSetting.class).value(), chatTypeTabs);
        var chatScreenshots = new ChatScreenshotService(
                Minecraft.getInstance(),
                settings,
                new TransparentChatCapture(Minecraft.getInstance()),
                new MinecraftChatSelectionSoundPlayer(Minecraft.getInstance(), settings)
        );
        context = new RalleContext(
                settings,
                new OwoSettingsScreenFactory(settings, chatLayout, navigation, guildRanks,
                        consumableHighlights.store(), cosmeticDirectory, cosmeticStyleSelection),
                chatLayout,
                chatBehavior,
                chatTypeTabs,
                chatInput,
                guildRanks,
                chatScreenshots,
                raidLfg,
                lfgKeybinds,
                autoRaidRequeue,
                hostPartyInvites,
                lfgSounds,
                consumableHighlights,
                cosmeticDirectory,
                cosmeticStyleSelection,
                cosmeticTextures
        );
        var pointAndLaugh = new org.kingdomfoxes.ralle.war.PointAndLaugh();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServer() != null) updateNotice.joined();
            else updateNotice.disconnected();
            foxGuildAccess.joined(client.getCurrentServer() == null ? "" : client.getCurrentServer().ip,
                    client.getUser().getProfileId());
            cosmeticDirectory.clear();
            cosmeticStyleSelection.clear();
            pointAndLaugh.reset();
            raidLfg.connectionChanged();
            guildRanks.connectionChanged();
            onboarding.postIfNeeded(body -> RalleChatMessages.post(client, body));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            updateNotice.disconnected();
            foxGuildAccess.clear();
            cosmeticDirectory.clear();
            cosmeticStyleSelection.clear();
            client.execute(cosmeticTextures::close);
            pointAndLaugh.reset();
            org.kingdomfoxes.ralle.ui.owo.PlayerHeadPresentation.clearSession();
            autoRaidRequeue.cancel();
            chatTypeTabs.resetSession();
            chatInput.reset();
            raidLfg.connectionChanged();
            guildRanks.connectionChanged();
            HqDistanceOverlay.clear();
            QueueAttributionService.disconnect();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateNotice.tick().ifPresent(release -> RalleChatMessages.post(client, UpdateMessages.body(release)));
            foxGuildAccess.tick();
            if (cosmeticReloadPending.getAndSet(false)) cosmeticTextures.close();
            var visibleCosmeticIds = new java.util.LinkedHashSet<java.util.UUID>();
            visibleCosmeticIds.add(client.getUser().getProfileId());
            if (client.level != null) client.level.players().forEach(player -> visibleCosmeticIds.add(player.getUUID()));
            raidLfg.store().state().lobbyList().forEach(lobby ->
                    lobby.members().forEach(member -> visibleCosmeticIds.add(member.minecraftUuid())));
            cosmeticDirectory.tick(visibleCosmeticIds);
            HqDistanceOverlay.tick();
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.CHAT_LAYOUT_TICK)) { chatLayout.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.CHAT_BEHAVIOR_TICK)) { chatBehavior.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.CHAT_INPUT_TICK)) { chatInput.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.GUILD_RANK_TICK)) { guildRanks.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.CHAT_SCREENSHOT_TICK)) { chatScreenshots.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.LFG_SERVICE_TICK)) { raidLfg.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.LFG_KEYBINDS_TICK)) { lfgKeybinds.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.REQUEUE_TICK)) { autoRaidRequeue.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.PARTY_INVITES_TICK)) { hostPartyInvites.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.NOTIFICATIONS_TICK)) { lfgNotificationOverlay.tick(); }
            try (var ignored = DiagnosticProfiler.measure(DiagnosticProfiler.Section.QUEUE_ATTRIBUTION_TICK)) { QueueAttributionService.tick(client); }
        });
        RaidRequeueMessages.register(autoRaidRequeue::observeChat);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            var server = client.getCurrentServer();
            var connection = client.getConnection();
            var reply = settings.setting(RalleSettings.POINT_AND_LAUGH_ID,
                    org.kingdomfoxes.ralle.api.settings.TextSetting.class).value();
            if (connection != null && !reply.isBlank()) {
                pointAndLaugh.observe(message.getString(), server != null && WynncraftHost.matches(server.ip),
                        overlay, reply, System.nanoTime() / 1_000_000L, connection::sendCommand);
            }
            return true;
        });
        ClientSendMessageEvents.COMMAND.register(chatTypeTabs::observeSentCommand);
        ClientSendMessageEvents.CHAT.register(chatTypeTabs::observeSentChat);
        ClientSendMessageEvents.CHAT.register(message -> chatInput.reset());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            var server = client.getCurrentServer();
            if (!overlay && server != null
                    && WynncraftHost.matches(server.ip)
                    && settings.setting(ChatTypeTabService.SETTING_ID, BooleanSetting.class).value()) {
                DirectMessageIdentityResolver.incomingSender(message, client.getUser().getName())
                        .ifPresent(chatTypeTabs::observeIncomingSender);
            }
            return true;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var ralleCommand = literal("ralle").then(literal("settings").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(context().settingsScreens().create(client.screen)));
                    return 1;
                })).then(literal("hud").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(ChatLayoutEditorScreen.forAllEnabled(
                            client.screen, context().chatLayout(), context().settings())));
                    return 1;
                })).then(literal("lfg").executes(command -> {
                    var client = Minecraft.getInstance();
                    client.schedule(() -> client.setScreen(new RaidLfgScreen(
                            client.screen, context().raidLfg(), regionDetector,
                            context().lfgSounds(), lfgNotifications, lockDebouncer)));
                    return 1;
                }));
            dispatcher.register(ralleCommand);
        });
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

    /** Read-only cosmetic projection for presentation code; never starts a request. */
    public static CosmeticIdentity cosmetic(java.util.UUID uuid) {
        return context == null ? null : context.cosmetics().cached(uuid);
    }

    public static long cosmeticsRevision() {
        return context == null ? 0 : context.cosmetics().presentationRevision();
    }

    public static CosmeticAppearance cosmeticAppearance(NameplateStyle style) {
        return context == null ? null : CosmeticAppearance.from(context.settings(), style);
    }
}
