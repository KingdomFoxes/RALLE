package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.kingdomfoxes.ralle.lfg.client.GuildTerritoryColors;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;
import org.kingdomfoxes.ralle.lfg.client.RaidRegionDetector;
import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Live Raid LFG browser backed by the persistent protocol-v1 service. */
public final class RaidLfgScreen extends BaseOwoScreen<FlowLayout> {
    private static final int GRID_WIDTH = 584;
    private static final int CARD_WIDTH = 286;
    private static final int COMPACT_JOIN_CONTROL_WIDTH = 42;
    private static final int HOST_CONTROL_GAP = 3;
    private static final int HOST_CONTROL_CONTENT_WIDTH = CARD_WIDTH - 18;
    private static final int HOST_CONTROL_LEFT_WIDTH = (HOST_CONTROL_CONTENT_WIDTH - HOST_CONTROL_GAP) / 2;
    private static final int HOST_CONTROL_RIGHT_WIDTH =
            HOST_CONTROL_CONTENT_WIDTH - HOST_CONTROL_GAP - HOST_CONTROL_LEFT_WIDTH;
    private static final int KICK_CONNECTOR_THICKNESS = 2;
    private static final int KICK_OUTLINE_THICKNESS = 3;
    private static final int KICK_READY_COLOR = 0xFFF2B84B;
    private static final int KICK_DANGER_COLOR = 0xFFFF3333;
    private static final Color REGION_GOOD = Color.ofRgb(0x00FF55);
    private static final Color REGION_MODERATE = Color.ofRgb(0xFFFF00);
    private static final Color REGION_POOR = Color.ofRgb(0xFF3333);

    private final Screen parent;
    private final RaidLfgService service;
    private final LfgSoundPlayer sounds;
    private final JoinCountdownState joinCountdown;
    private final KickTargetingState kickTargeting;
    private final Set<UUID> expandedLobbies = new HashSet<>();
    private final Map<UUID, FlowLayout> kickRows = new HashMap<>();
    private StatusFilter statusFilter = StatusFilter.OPEN;
    private RaidFilter raidFilter = RaidFilter.ALL;
    private RegionFilter regionFilter = RegionFilter.ALL;
    private LfgProtocol.Region currentRegion = LfgProtocol.Region.EU;
    private FlowLayout root;
    private FlowLayout gridHost;
    private LabelComponent footerStatus;
    private ButtonComponent createButton;
    private ButtonComponent refreshButton;
    private ButtonComponent statusButton;
    private ButtonComponent raidButton;
    private ButtonComponent regionButton;
    private ScrollContainer<FlowLayout> scroll;
    private UUID scrollTarget;
    private FlowLayout scrollTargetComponent;
    private long renderedRevision = Long.MIN_VALUE;
    private RaidLfgService.LifecycleState renderedLifecycle;
    private int renderedCountdownSeconds = -1;
    private int renderedPingSeconds = -1;
    private UUID kickLobbyId;
    private ButtonComponent kickButton;
    private ButtonComponent lockButton;

    public RaidLfgScreen(Screen parent, RaidLfgService service) {
        this(parent, service, LfgSoundPlayer.SILENT);
    }

    public RaidLfgScreen(Screen parent, RaidLfgService service, LfgSoundPlayer sounds) {
        this(parent, service, RaidRegionDetector.UNAVAILABLE, System::nanoTime, sounds);
    }

    public RaidLfgScreen(Screen parent, RaidLfgService service, RaidRegionDetector regionDetector) {
        this(parent, service, regionDetector, System::nanoTime, LfgSoundPlayer.SILENT);
    }

    RaidLfgScreen(Screen parent, RaidLfgService service, RaidRegionDetector regionDetector, LongSupplier nanoTime) {
        this(parent, service, regionDetector, nanoTime, LfgSoundPlayer.SILENT);
    }

    RaidLfgScreen(Screen parent, RaidLfgService service, RaidRegionDetector regionDetector,
                  LongSupplier nanoTime, LfgSoundPlayer sounds) {
        this.parent = parent;
        this.service = service;
        this.sounds = sounds;
        this.currentRegion = regionDetector.detect().orElse(LfgProtocol.Region.EU);
        this.joinCountdown = new JoinCountdownState(nanoTime);
        this.kickTargeting = new KickTargetingState(nanoTime);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var panel = UIContainers.verticalFlow(Sizing.fixed(620), Sizing.fill(92));
        panel.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        panel.child(RalleHeader.create(Component.literal("ALLY GRAID FINDER")));

        var filters = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        filters.gap(6).padding(Insets.left(4)).verticalAlignment(VerticalAlignment.CENTER);

        createButton = UIComponents.button(RalleTheme.ui(Component.literal("+")), ignored -> openCreateModal());
        createButton.sizing(Sizing.fixed(30), Sizing.fixed(20));
        createButton.renderer(RalleButtonRenderers.primary());
        createButton.tooltip(RalleTheme.ui(Component.literal("Create a raid lobby")));

        statusButton = UIComponents.button(statusLabel(), ignored -> {});
        raidButton = UIComponents.button(raidLabel(), ignored -> {});
        regionButton = UIComponents.button(regionLabel(), ignored -> {});
        statusButton.horizontalSizing(Sizing.fixed(164));
        raidButton.horizontalSizing(Sizing.fixed(164));
        regionButton.horizontalSizing(Sizing.fixed(164));
        statusButton.renderer(RalleButtonRenderers.selectable(() -> statusFilter != StatusFilter.OPEN));
        raidButton.renderer(RalleButtonRenderers.selectable(() -> raidFilter != RaidFilter.ALL));
        regionButton.renderer(RalleButtonRenderers.selectable(() -> regionFilter != RegionFilter.ALL));
        statusButton.onPress(button -> openStatusDropdown(button));
        raidButton.onPress(button -> openRaidDropdown(button));
        regionButton.onPress(button -> openRegionDropdown(button));

        refreshButton = UIComponents.button(Component.empty(), ignored -> service.refresh());
        refreshButton.sizing(Sizing.fixed(30), Sizing.fixed(20));
        refreshButton.renderer(RalleButtonRenderers.refresh());
        refreshButton.tooltip(RalleTheme.ui(Component.literal("Request a complete fresh snapshot")));
        filters.child(createButton).child(statusButton).child(raidButton).child(regionButton).child(refreshButton);
        panel.child(filters);

        gridHost = UIContainers.verticalFlow(Sizing.fixed(GRID_WIDTH), Sizing.content());
        scroll = UIContainers.verticalScroll(Sizing.fixed(592), Sizing.fill(100), gridHost);
        scroll.scrollbarThiccness(4).scrollStep(36);
        panel.child(scroll);

        var footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.verticalAlignment(VerticalAlignment.CENTER);
        footerStatus = UIComponents.label(RalleTheme.ui(Component.literal("Initializing..."))).color(RalleTheme.MUTED);
        footer.child(footerStatus);
        var spacer = UIComponents.spacer();
        spacer.verticalSizing(Sizing.fixed(0));
        footer.child(spacer);
        var close = UIComponents.button(RalleTheme.ui(Component.translatable("gui.done")), ignored -> onClose());
        close.horizontalSizing(Sizing.fixed(92)).margins(Insets.left(16));
        close.renderer(RalleButtonRenderers.neutral());
        footer.child(close);
        panel.child(footer);
        root.child(panel);
        refreshFromService(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (scrollTargetComponent != null) {
            scroll.scrollTo(scrollTargetComponent);
            scrollTargetComponent = null;
            scrollTarget = null;
        }
        var state = service.store().state();
        if (state.revision() != renderedRevision || service.lifecycle() != renderedLifecycle || service.focusLobbyId() != null) {
            refreshFromService(false);
        }
        tickJoinCountdown();
        tickKickTargeting();
        tickPingCooldown();
    }

    private void refreshFromService(boolean force) {
        if (gridHost == null) return;
        var state = service.store().state();
        var lifecycle = service.lifecycle();
        var focus = service.focusLobbyId();
        if (!force && state.revision() == renderedRevision && lifecycle == renderedLifecycle && focus == null) return;
        renderedRevision = state.revision();
        renderedLifecycle = lifecycle;
        if (focus != null) {
            statusFilter = StatusFilter.ALL;
            raidFilter = RaidFilter.ALL;
            regionFilter = RegionFilter.ALL;
            expandedLobbies.add(focus);
            scrollTarget = focus;
            service.clearFocus();
            statusButton.setMessage(statusLabel());
            raidButton.setMessage(raidLabel());
            regionButton.setMessage(regionLabel());
        }
        boolean online = lifecycle == RaidLfgService.LifecycleState.ONLINE;
        refreshButton.active = online;
        createButton.active = online && state.capabilities() != null && state.capabilities().create() && !service.pendingCreate();
        footerStatus.text(RalleTheme.ui(Component.literal(statusText(lifecycle))));
        boolean onlineError = online && !"Live".equals(service.statusMessage());
        footerStatus.color(onlineError ? Color.ofRgb(0xFF6B6B)
                : online ? RalleTheme.POSITIVE : lifecycle == RaidLfgService.LifecycleState.OUTDATED
                || lifecycle == RaidLfgService.LifecycleState.INELIGIBLE ? RalleTheme.ACCENT : RalleTheme.MUTED);
        rebuildGrid();
    }

    private void rebuildGrid() {
        cancelUnavailableJoinCountdown();
        kickRows.clear();
        kickButton = null;
        lockButton = null;
        renderedPingSeconds = -1;
        gridHost.clearChildren();
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) {
            validateKickTargeting();
            gridHost.child(statePanel(service.statusMessage()));
            return;
        }
        var visible = service.store().state().lobbyList().stream().filter(this::matchesFilters).toList();
        if (visible.isEmpty()) {
            validateKickTargeting();
            gridHost.child(statePanel("No parties match these filters."));
            return;
        }
        int rows = (visible.size() + 1) / 2;
        GridLayout grid = UIContainers.grid(Sizing.fixed(GRID_WIDTH), Sizing.content(), rows, 2);
        grid.padding(Insets.of(2));
        for (int i = 0; i < visible.size(); i++) {
            var card = lobbyCard(visible.get(i));
            grid.child(card, i / 2, i % 2);
            if (visible.get(i).lobbyId().equals(scrollTarget)) scrollTargetComponent = card;
        }
        gridHost.child(grid);
        validateKickTargeting();
    }

    private FlowLayout statePanel(String message) {
        var panel = UIContainers.verticalFlow(Sizing.fixed(GRID_WIDTH), Sizing.fixed(100));
        panel.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER)
                .surface(RalleSurfaces.NAVY_PANEL);
        panel.child(UIComponents.label(RalleTheme.ui(Component.literal(message))).color(RalleTheme.MUTED).maxWidth(GRID_WIDTH - 40));
        if (service.lifecycle() == RaidLfgService.LifecycleState.OUTDATED && service.releaseUrl() != null
                && !service.releaseUrl().isBlank()) {
            var link = Component.literal("Open current Modrinth release").withStyle(style -> style
                    .withColor(0xF2B84B).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(service.releaseUrl()))));
            panel.child(UIComponents.label(link));
        }
        return panel;
    }

    private FlowLayout lobbyCard(LfgProtocol.Lobby lobby) {
        var card = UIContainers.verticalFlow(Sizing.fixed(CARD_WIDTH), Sizing.content());
        card.gap(5).padding(Insets.of(9)).surface(RalleSurfaces.NAVY_ROW).margins(Insets.of(3));
        boolean expanded = expandedLobbies.contains(lobby.lobbyId());
        var summary = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        summary.verticalAlignment(VerticalAlignment.CENTER).cursorStyle(CursorStyle.HAND)
                .tooltip(RalleTheme.ui(Component.literal(expanded ? "Hide party details" : "Show party details")));
        var raidDetails = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        raidDetails.verticalAlignment(VerticalAlignment.CENTER);
        raidDetails.child(UIComponents.item(new ItemStack(raidIcon(lobby.raidType()))).showOverlay(false).margins(Insets.right(6)));
        raidDetails.child(UIComponents.label(RalleTheme.ui(Component.literal(raidName(lobby.raidType())))).shadow(true).color(RalleTheme.ACCENT));
        summary.child(raidDetails);
        var spacer = UIComponents.spacer();
        spacer.verticalSizing(Sizing.fixed(0));
        summary.child(spacer);
        summary.child(UIComponents.label(RalleTheme.ui(Component.literal(lobby.members().size() + "/" + lobby.capacity())))
                .color(RalleTheme.POSITIVE).margins(Insets.right(6)));
        if (lobby.locked()) {
            summary.child(UIComponents.label(RalleTheme.ui(Component.literal("LOCKED")))
                    .color(RalleTheme.ACCENT).margins(Insets.right(3)));
        }
        if (!expanded) {
            if (joinCountdown.activeFor(lobby.lobbyId())) {
                summary.child(joinCountdownControls(true));
            } else if (collapsedActionVisible(lobby)) {
                summary.child(collapsedAction(lobby));
            }
        }
        summary.child(UIComponents.label(Component.literal(expanded ? " ▼" : " ▶")).color(RalleTheme.MUTED).margins(Insets.left(6)));
        summary.mouseDown().subscribe((click, doubled) -> {
            if (expanded) expandedLobbies.remove(lobby.lobbyId());
            else expandedLobbies.add(lobby.lobbyId());
            rebuildGrid();
            return true;
        });
        card.child(summary);
        if (!expanded) return card;

        var details = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        details.child(UIComponents.label(RalleTheme.ui(Component.literal("[" + lobby.region().name() + "]"))).color(regionColor(lobby.region())));
        if (lobby.note() != null && !lobby.note().isBlank()) {
            details.child(UIComponents.label(RalleTheme.ui(Component.literal(" " + lobby.note()))).color(RalleTheme.MUTED).maxWidth(CARD_WIDTH - 54));
        }
        card.child(details);
        var roster = UIContainers.grid(Sizing.fill(100), Sizing.content(), 2, 2);
        for (int slot = 0; slot < lobby.capacity(); slot++) roster.child(rosterSlot(lobby, slot), slot / 2, slot % 2);
        card.child(roster);
        if (lobby.hostedBy(viewerId())) {
            card.child(hostControls(lobby));
        } else if (joinCountdown.activeFor(lobby.lobbyId())) {
            card.child(joinCountdownControls(false));
        } else {
            var action = expandedAction(lobby);
            action.horizontalSizing(Sizing.fill(100));
            card.child(action);
        }
        return card;
    }

    private ButtonComponent collapsedAction(LfgProtocol.Lobby lobby) {
        var action = collapsedActionFor(lobby, viewerId());
        var button = UIComponents.button(RalleTheme.ui(Component.literal(action.label())), ignored -> {
            if (action.requiresConfirmation()) {
                openDisbandConfirmation(lobby);
            } else if (action.kind() == CardActionKind.JOIN) {
                startJoinCountdown(lobby);
            } else {
                performAction(lobby, action.kind().protocolAction);
            }
        });
        button.sizing(Sizing.fixed(action.kind() == CardActionKind.DISBAND ? 64 : 52), Sizing.fixed(20));
        button.renderer(action.destructive() ? RalleButtonRenderers.destructive() : RalleButtonRenderers.primary());
        boolean allowed = action.kind() != CardActionKind.JOIN || joinAvailable(lobby);
        button.active = allowed
                && service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && !service.pending(lobby.lobbyId(), action.kind().protocolAction)
                && (action.kind() != CardActionKind.JOIN || !joinCountdown.active());
        button.tooltip(RalleTheme.ui(Component.literal(switch (action.kind()) {
            case DISBAND -> "Disband this party";
            case LEAVE -> "Leave this party";
            case JOIN -> joinCountdown.active()
                    ? "Cancel the current join countdown first"
                    : "Begin the join countdown";
        })));
        return button;
    }

    private boolean collapsedActionVisible(LfgProtocol.Lobby lobby) {
        return collapsedActionVisible(lobby, viewerId());
    }

    static boolean collapsedActionVisible(LfgProtocol.Lobby lobby, UUID viewerId) {
        return !lobby.locked() || viewerId != null && lobby.contains(viewerId);
    }

    private FlowLayout hostControls(LfgProtocol.Lobby lobby) {
        var controls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        controls.gap(3);

        var disband = expandedAction(lobby);
        disband.horizontalSizing(Sizing.fill(100));
        controls.child(disband);

        var middle = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        middle.gap(HOST_CONTROL_GAP);
        kickButton = UIComponents.button(RalleTheme.ui(Component.literal("Kick")), ignored -> {
            if (kickTargeting.holding()) sounds.playKickHoldCancelled();
            if (kickTargeting.toggle()) kickLobbyId = lobby.lobbyId();
            else kickLobbyId = null;
            rebuildGrid();
        });
        kickButton.sizing(Sizing.fixed(HOST_CONTROL_LEFT_WIDTH), Sizing.fixed(20));
        kickButton.renderer(RalleButtonRenderers.warning(kickTargeting::active));
        kickButton.active = service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && !service.pending(lobby.lobbyId(), "kick");
        kickButton.tooltip(RalleTheme.ui(Component.literal(
                kickTargeting.active() ? "Select a member, then hold left mouse for 1.5 seconds"
                        : "Choose a member to kick")));

        lockButton = UIComponents.button(
                RalleTheme.ui(Component.literal(lobby.locked() ? "Unlock" : "Lock")),
                ignored -> performLock(lobby));
        lockButton.sizing(Sizing.fixed(HOST_CONTROL_RIGHT_WIDTH), Sizing.fixed(20));
        lockButton.renderer(RalleButtonRenderers.neutral());
        lockButton.active = service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && !service.pending(lobby.lobbyId(), "lock");
        lockButton.tooltip(RalleTheme.ui(Component.literal(
                lobby.locked() ? "Allow new members to join" : "Prevent new members from joining")));
        middle.child(kickButton).child(lockButton);
        controls.child(middle);

        int cooldown = service.pingCooldownSeconds(lobby.lobbyId());
        var ping = UIComponents.button(RalleTheme.ui(Component.literal(
                cooldown > 0 ? "Ping (" + cooldown + "s)" : "Ping")), ignored -> performPing(lobby));
        ping.sizing(Sizing.fill(100), Sizing.fixed(20));
        ping.renderer(RalleButtonRenderers.neutral());
        ping.active = service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && cooldown == 0 && !service.pending(lobby.lobbyId(), "ping");
        ping.tooltip(RalleTheme.ui(Component.literal(
                cooldown > 0 ? "Party ping is on cooldown" : "Notify every current party member")));
        controls.child(ping);
        renderedPingSeconds = cooldown;
        return controls;
    }

    private ButtonComponent expandedAction(LfgProtocol.Lobby lobby) {
        var viewerId = viewerId();
        var action = expandedActionFor(lobby, viewerId);
        boolean member = viewerId != null && lobby.contains(viewerId);
        boolean allowed = action.kind() == CardActionKind.DISBAND
                || lobby.capabilities() != null && (member ? lobby.capabilities().leave() : lobby.capabilities().join());
        var button = UIComponents.button(RalleTheme.ui(Component.literal(action.label())), ignored -> {
            if (action.requiresConfirmation()) openDisbandConfirmation(lobby);
            else if (action.kind() == CardActionKind.JOIN) startJoinCountdown(lobby);
            else performAction(lobby, action.kind().protocolAction);
        });
        button.sizing(Sizing.fixed(72), Sizing.fixed(20));
        button.renderer(action.destructive() ? RalleButtonRenderers.destructive() : RalleButtonRenderers.primary());
        button.active = allowed
                && !service.pending(lobby.lobbyId(), action.kind().protocolAction)
                && (action.kind() != CardActionKind.JOIN || !joinCountdown.active());
        if (!allowed && lobby.capabilities() != null) {
            var reason = lobby.capabilities().reason(member ? "leave" : "join");
            if (reason != null) button.tooltip(RalleTheme.ui(Component.literal(capabilityReason(reason))));
        } else if (action.kind() == CardActionKind.JOIN && joinCountdown.active()) {
            button.tooltip(RalleTheme.ui(Component.literal("Cancel the current join countdown first")));
        }
        return button;
    }

    private FlowLayout joinCountdownControls(boolean compact) {
        var controls = UIContainers.horizontalFlow(
                compact ? Sizing.fixed(COMPACT_JOIN_CONTROL_WIDTH * 2 + 3) : Sizing.fill(100),
                Sizing.fixed(20)
        );
        controls.gap(3).verticalAlignment(VerticalAlignment.CENTER);

        var countdown = UIComponents.button(
                RalleTheme.ui(Component.literal(Integer.toString(joinCountdown.secondsRemaining()))),
                ignored -> {}
        );
        countdown.sizing(compact ? Sizing.fixed(COMPACT_JOIN_CONTROL_WIDTH) : Sizing.fill(50), Sizing.fixed(20));
        countdown.renderer(RalleButtonRenderers.countdown(joinCountdown::remainingFraction));
        countdown.tooltip(RalleTheme.ui(Component.literal("Joining when the countdown reaches zero")));

        var cancel = UIComponents.button(RalleTheme.ui(Component.literal("Cancel")), ignored -> {
            joinCountdown.cancel();
            renderedCountdownSeconds = -1;
            rebuildGrid();
        });
        cancel.sizing(compact ? Sizing.fixed(COMPACT_JOIN_CONTROL_WIDTH) : Sizing.fill(50), Sizing.fixed(20));
        cancel.renderer(RalleButtonRenderers.destructive());
        cancel.tooltip(RalleTheme.ui(Component.literal("Cancel joining this party")));

        controls.child(countdown).child(cancel);
        return controls;
    }

    private void startJoinCountdown(LfgProtocol.Lobby lobby) {
        if (!joinAvailable(lobby) || !joinCountdown.start(lobby.lobbyId())) return;
        renderedCountdownSeconds = joinCountdown.secondsRemaining();
        rebuildGrid();
    }

    private void tickJoinCountdown() {
        if (!joinCountdown.active()) return;
        if (!joinCountdownAvailable()) {
            joinCountdown.cancel();
            renderedCountdownSeconds = -1;
            rebuildGrid();
            return;
        }
        if (joinCountdown.elapsed()) {
            var lobby = lobby(joinCountdown.lobbyId());
            joinCountdown.cancel();
            renderedCountdownSeconds = -1;
            if (lobby != null && joinAvailable(lobby)) performAction(lobby, CardActionKind.JOIN.protocolAction);
            else rebuildGrid();
            return;
        }
        int seconds = joinCountdown.secondsRemaining();
        if (seconds != renderedCountdownSeconds) {
            renderedCountdownSeconds = seconds;
            rebuildGrid();
        }
    }

    private void cancelUnavailableJoinCountdown() {
        if (joinCountdown.active() && !joinCountdownAvailable()) {
            joinCountdown.cancel();
            renderedCountdownSeconds = -1;
        }
    }

    private boolean joinCountdownAvailable() {
        var lobby = lobby(joinCountdown.lobbyId());
        return lobby != null && matchesFilters(lobby) && joinAvailable(lobby);
    }

    private boolean joinAvailable(LfgProtocol.Lobby lobby) {
        var viewerId = viewerId();
        return service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && viewerId != null
                && !lobby.contains(viewerId)
                && lobby.capabilities() != null
                && lobby.capabilities().join()
                && !service.pending(lobby.lobbyId(), CardActionKind.JOIN.protocolAction);
    }

    private LfgProtocol.Lobby lobby(UUID lobbyId) {
        if (lobbyId == null) return null;
        return service.store().state().lobbyList().stream()
                .filter(lobby -> lobby.lobbyId().equals(lobbyId))
                .findFirst()
                .orElse(null);
    }

    private void performAction(LfgProtocol.Lobby lobby, String action) {
        var future = switch (action) {
            case "disband" -> service.disband(lobby.lobbyId());
            case "leave" -> service.leave(lobby.lobbyId());
            default -> service.join(lobby.lobbyId());
        };
        future.whenComplete((ignored, failure) -> minecraft.execute(() -> refreshFromService(true)));
        rebuildGrid();
    }

    private void performLock(LfgProtocol.Lobby lobby) {
        if (lockButton != null) {
            lockButton.active = false;
            lockButton.setMessage(RalleTheme.ui(Component.literal(lobby.locked() ? "Unlocking..." : "Locking...")));
        }
        service.setLocked(lobby.lobbyId(), !lobby.locked())
                .whenComplete((ignored, failure) -> minecraft.execute(() -> refreshFromService(true)));
    }

    private void performPing(LfgProtocol.Lobby lobby) {
        if (service.pingCooldownSeconds(lobby.lobbyId()) > 0) return;
        service.ping(lobby.lobbyId())
                .whenComplete((ignored, failure) -> minecraft.execute(() -> refreshFromService(true)));
        rebuildGrid();
    }

    private FlowLayout rosterSlot(LfgProtocol.Lobby lobby, int slot) {
        var row = UIContainers.horizontalFlow(Sizing.fixed(130), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER).padding(Insets.of(2));
        if (slot >= lobby.members().size()) {
            row.child(UIComponents.item(new ItemStack(Items.GRAY_STAINED_GLASS_PANE)).showOverlay(false).margins(Insets.right(4)));
            row.child(UIComponents.label(RalleTheme.ui(Component.literal("Open slot"))).color(RalleTheme.MUTED));
            return row;
        }
        var member = lobby.members().get(slot);
        var head = new PlayerFaceComponent(member.minecraftUuid().toString(), member.ign(), 16,
                GuildTerritoryColors.parse(member.guild().color()));
        head.tooltip(RalleTheme.ui(Component.literal("[" + member.guild().tag() + "]"))).margins(Insets.right(4));
        row.child(head);
        var label = Component.empty();
        if (member.role() == LfgProtocol.MemberRole.HOST) label.append(Component.literal("★ "));
        label.append(RalleTheme.ui(Component.literal(member.ign())));
        row.child(UIComponents.label(label).color(Color.WHITE));
        if (lobby.hostedBy(viewerId()) && member.role() != LfgProtocol.MemberRole.HOST) {
            kickRows.put(member.minecraftUuid(), row);
            row.mouseDown().subscribe((click, doubled) -> {
                if (click.button() != 0 || !lobby.lobbyId().equals(kickLobbyId)) return false;
                if (!kickTargeting.press(member.minecraftUuid())) return false;
                sounds.playKickHoldStart();
                return true;
            });
            row.mouseUp().subscribe(click -> {
                if (click.button() != 0) return false;
                if (!kickTargeting.release(member.minecraftUuid())) return false;
                sounds.playKickHoldCancelled();
                return true;
            });
        }
        return row;
    }

    private void tickKickTargeting() {
        var targetId = kickTargeting.completedTarget();
        if (targetId == null || kickLobbyId == null) return;
        var lobby = lobby(kickLobbyId);
        var target = lobby == null ? null : lobby.members().stream()
                .filter(member -> member.minecraftUuid().equals(targetId)
                        && member.role() != LfgProtocol.MemberRole.HOST)
                .findFirst().orElse(null);
        if (lobby == null || target == null || !lobby.hostedBy(viewerId())) {
            kickTargeting.reset();
            kickLobbyId = null;
            rebuildGrid();
            return;
        }
        service.kick(lobby.lobbyId(), target.minecraftUuid(), target.ign())
                .whenComplete((ignored, failure) -> minecraft.execute(() -> {
                    if (failure == null) sounds.playKickSucceeded();
                    kickTargeting.reset();
                    kickLobbyId = null;
                    refreshFromService(true);
                }));
        rebuildGrid();
    }

    private void tickPingCooldown() {
        if (renderedPingSeconds < 0) return;
        var hostLobby = service.store().state().lobbyList().stream()
                .filter(candidate -> candidate.hostedBy(viewerId()))
                .findFirst().orElse(null);
        int seconds = hostLobby == null ? -1 : service.pingCooldownSeconds(hostLobby.lobbyId());
        if (seconds != renderedPingSeconds) rebuildGrid();
    }

    private void validateKickTargeting() {
        if (!kickTargeting.active()) return;
        var lobby = lobby(kickLobbyId);
        if (lobby == null || !lobby.hostedBy(viewerId()) || !expandedLobbies.contains(kickLobbyId)
                || kickButton == null) {
            if (kickTargeting.holding()) sounds.playKickHoldCancelled();
            kickTargeting.reset();
            kickLobbyId = null;
            return;
        }
        var target = kickTargeting.visualTarget();
        if (target != null && !kickRows.containsKey(target) && kickTargeting.leave(target)) {
            sounds.playKickHoldCancelled();
        }
    }

    private void updateKickHover(int mouseX, int mouseY) {
        if (!kickTargeting.active() || kickTargeting.submitted()) return;
        UUID next = null;
        for (var entry : kickRows.entrySet()) {
            if (entry.getValue().isInBoundingBox(mouseX, mouseY)) {
                next = entry.getKey();
                break;
            }
        }
        var previous = kickTargeting.hovered();
        if (previous != null && !previous.equals(next) && kickTargeting.leave(previous)) {
            sounds.playKickHoldCancelled();
        }
        if (next != null && kickTargeting.hover(next)) sounds.playKickTargetHover();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateKickHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderKickTargeting(graphics);
    }

    private void renderKickTargeting(GuiGraphics graphics) {
        if (!kickTargeting.active() || kickButton == null) return;
        var target = kickRows.get(kickTargeting.visualTarget());
        if (target == null) return;

        var connector = connectorBetween(
                new SelectionBox(kickButton.getX(), kickButton.getY(),
                        kickButton.getWidth(), kickButton.getHeight()),
                new SelectionBox(target.x(), target.y(), target.width(), target.height()));
        drawThickLine(graphics, connector.startX(), connector.startY(),
                connector.endX(), connector.endY(), KICK_CONNECTOR_THICKNESS, KICK_READY_COLOR);
        int targetOutlineColor = lerpArgb(KICK_READY_COLOR, KICK_DANGER_COLOR, kickTargeting.progress());
        drawThickOutline(graphics, target.x(), target.y(), target.width(), target.height(),
                KICK_OUTLINE_THICKNESS, targetOutlineColor);
        drawThickOutline(graphics, kickButton.getX(), kickButton.getY(),
                kickButton.getWidth(), kickButton.getHeight(), KICK_OUTLINE_THICKNESS, KICK_READY_COLOR);
    }

    private static void drawThickOutline(GuiGraphics graphics, int x, int y, int width, int height,
                                         int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    static int lerpArgb(int from, int to, double progress) {
        double amount = Math.clamp(progress, 0d, 1d);
        int alpha = lerpChannel(from >>> 24, to >>> 24, amount);
        int red = lerpChannel(from >>> 16, to >>> 16, amount);
        int green = lerpChannel(from >>> 8, to >>> 8, amount);
        int blue = lerpChannel(from, to, amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int lerpChannel(int from, int to, double progress) {
        return (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
    }

    static ConnectorLine connectorBetween(SelectionBox start, SelectionBox end) {
        double startCenterX = start.x() + start.width() / 2d;
        double startCenterY = start.y() + start.height() / 2d;
        double endCenterX = end.x() + end.width() / 2d;
        double endCenterY = end.y() + end.height() / 2d;
        double dx = endCenterX - startCenterX;
        double dy = endCenterY - startCenterY;
        if (dx == 0 && dy == 0) {
            int x = (int) Math.round(startCenterX);
            int y = (int) Math.round(startCenterY);
            return new ConnectorLine(x, y, x, y);
        }

        double startScale = boundaryScale(start, dx, dy);
        double endScale = boundaryScale(end, -dx, -dy);
        return new ConnectorLine(
                (int) Math.round(startCenterX + dx * startScale),
                (int) Math.round(startCenterY + dy * startScale),
                (int) Math.round(endCenterX - dx * endScale),
                (int) Math.round(endCenterY - dy * endScale));
    }

    private static double boundaryScale(SelectionBox box, double dx, double dy) {
        double horizontal = dx == 0 ? Double.POSITIVE_INFINITY : box.width() / 2d / Math.abs(dx);
        double vertical = dy == 0 ? Double.POSITIVE_INFINITY : box.height() / 2d / Math.abs(dy);
        return Math.min(horizontal, vertical);
    }

    private static void drawThickLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                      int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) steps = 1;
        int offset = thickness / 2;
        for (int step = 0; step <= steps; step++) {
            int x = x1 + Math.round(dx * (step / (float) steps));
            int y = y1 + Math.round(dy * (step / (float) steps));
            graphics.fill(x - offset, y - offset, x - offset + thickness, y - offset + thickness, color);
        }
    }

    record SelectionBox(int x, int y, int width, int height) {}

    record ConnectorLine(int startX, int startY, int endX, int endY) {}

    private void openDisbandConfirmation(LfgProtocol.Lobby lobby) {
        if (root == null || service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) return;
        var content = UIContainers.verticalFlow(Sizing.fixed(300), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.literal("DISBAND PARTY"))).color(RalleTheme.ACCENT));
        content.child(UIComponents.label(RalleTheme.ui(Component.literal(
                "Disband this party? The listing will be removed for everyone."))).color(RalleTheme.MUTED).maxWidth(276));

        var controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(8);
        var overlayHolder = new OverlayContainer<?>[1];
        var confirm = UIComponents.button(RalleTheme.ui(Component.literal("Disband")), ignored -> {
            overlayHolder[0].remove();
            performAction(lobby, CardActionKind.DISBAND.protocolAction);
        });
        confirm.horizontalSizing(Sizing.fill(50));
        confirm.renderer(RalleButtonRenderers.destructive());
        var cancel = UIComponents.button(RalleTheme.ui(Component.literal("Cancel")), ignored -> overlayHolder[0].remove());
        cancel.horizontalSizing(Sizing.fill(50));
        cancel.renderer(RalleButtonRenderers.neutral());
        controls.child(confirm).child(cancel);
        content.child(controls);

        var overlay = UIContainers.overlay(content).closeOnClick(false);
        overlayHolder[0] = overlay;
        root.child(overlay);
    }

    private UUID viewerId() {
        var viewer = service.store().state().viewer();
        return viewer == null ? null : viewer.minecraftUuid();
    }

    static CardAction collapsedActionFor(LfgProtocol.Lobby lobby, UUID viewerId) {
        if (viewerId != null && lobby.hostedBy(viewerId)) {
            return new CardAction(CardActionKind.DISBAND, "Disband", true, true);
        }
        if (viewerId != null && lobby.contains(viewerId)) {
            return new CardAction(CardActionKind.LEAVE, "Leave", true, false);
        }
        return new CardAction(CardActionKind.JOIN, "Join", false, false);
    }

    static CardAction expandedActionFor(LfgProtocol.Lobby lobby, UUID viewerId) {
        if (viewerId != null && lobby.hostedBy(viewerId)) {
            return new CardAction(CardActionKind.DISBAND, "Disband", true, true);
        }
        if (viewerId != null && lobby.contains(viewerId)) {
            return new CardAction(CardActionKind.LEAVE, "Leave", true, false);
        }
        return new CardAction(CardActionKind.JOIN, disabledJoinLabel(lobby), false, false);
    }

    private void openCreateModal() {
        if (root == null || service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) return;
        var content = UIContainers.verticalFlow(Sizing.fixed(320), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.literal("CREATE RAID LOBBY"))).color(RalleTheme.ACCENT));
        var selectedRaid = new LfgProtocol.RaidType[]{LfgProtocol.RaidType.DAILIES};
        var selectedRegion = new LfgProtocol.Region[]{currentRegion};
        var raid = UIComponents.button(RalleTheme.ui(Component.literal("Raid: Dailies")), ignored -> {});
        raid.horizontalSizing(Sizing.fill(100));
        raid.renderer(RalleButtonRenderers.neutral());
        raid.onPress(button -> {
            var values = LfgProtocol.RaidType.values();
            selectedRaid[0] = values[(selectedRaid[0].ordinal() + 1) % values.length];
            button.setMessage(RalleTheme.ui(Component.literal("Raid: " + raidName(selectedRaid[0]))));
        });
        var region = UIComponents.button(RalleTheme.ui(Component.literal("Region: " + selectedRegion[0])), ignored -> {});
        region.horizontalSizing(Sizing.fill(100));
        region.renderer(RalleButtonRenderers.neutral());
        region.onPress(button -> {
            var values = LfgProtocol.Region.values();
            selectedRegion[0] = values[(selectedRegion[0].ordinal() + 1) % values.length];
            button.setMessage(RalleTheme.ui(Component.literal("Region: " + selectedRegion[0])));
        });
        var noteValue = new String[]{""};
        var note = UIComponents.textBox(Sizing.fill(100));
        note.setMaxLength(80);
        note.setHint(RalleTheme.ui(Component.literal("Optional note (80 characters)")));
        note.onChanged().subscribe(value -> noteValue[0] = value);
        var error = UIComponents.label(RalleTheme.ui(Component.literal(""))).color(Color.ofRgb(0xFF6B6B)).maxWidth(296);
        content.child(raid).child(region).child(note).child(error);
        var controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(8);
        var overlayHolder = new OverlayContainer<?>[1];
        var submit = UIComponents.button(RalleTheme.ui(Component.literal("Create")), ignored -> {
            ignored.active = false;
            error.text(RalleTheme.ui(Component.literal("Creating..."))).color(RalleTheme.MUTED);
            service.create(selectedRaid[0], selectedRegion[0], noteValue[0]).whenComplete((mutation, failure) -> minecraft.execute(() -> {
                if (failure == null) {
                    expandedLobbies.add(mutation.lobby().lobbyId());
                    statusFilter = StatusFilter.ALL;
                    raidFilter = RaidFilter.ALL;
                    regionFilter = RegionFilter.ALL;
                    overlayHolder[0].remove();
                    refreshFromService(true);
                    return;
                }
                var cause = unwrap(failure);
                if (cause instanceof LfgGatewayException gateway && "RAID_ALREADY_LISTED".equals(gateway.error().code())) {
                    overlayHolder[0].remove();
                    refreshFromService(true);
                } else {
                    ignored.active = true;
                    error.text(RalleTheme.ui(Component.literal(cause.getMessage() == null ? "Lobby creation failed." : cause.getMessage())))
                            .color(Color.ofRgb(0xFF6B6B));
                }
            }));
        });
        submit.horizontalSizing(Sizing.fill(50));
        submit.renderer(RalleButtonRenderers.primary());
        var cancel = UIComponents.button(RalleTheme.ui(Component.literal("Cancel")), ignored -> overlayHolder[0].remove());
        cancel.horizontalSizing(Sizing.fill(50));
        cancel.renderer(RalleButtonRenderers.neutral());
        controls.child(submit).child(cancel);
        content.child(controls);
        var overlay = UIContainers.overlay(content).closeOnClick(false);
        overlayHolder[0] = overlay;
        root.child(overlay);
    }

    private void openStatusDropdown(ButtonComponent trigger) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child, trigger.x(), trigger.y() + trigger.height(), menu -> {
            for (var option : StatusFilter.values()) menu.button(RalleTheme.ui(Component.literal(option.label)), dropdown -> {
                statusFilter = option; trigger.setMessage(statusLabel()); rebuildGrid(); root.removeChild(dropdown);
            });
        });
    }

    private void openRaidDropdown(ButtonComponent trigger) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child, trigger.x(), trigger.y() + trigger.height(), menu -> {
            for (var option : RaidFilter.values()) menu.button(RalleTheme.ui(Component.literal(option.label)), dropdown -> {
                raidFilter = option; trigger.setMessage(raidLabel()); rebuildGrid(); root.removeChild(dropdown);
            });
        });
    }

    private void openRegionDropdown(ButtonComponent trigger) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child, trigger.x(), trigger.y() + trigger.height(), menu -> {
            for (var option : RegionFilter.values()) menu.button(RalleTheme.ui(Component.literal(option.label)), dropdown -> {
                regionFilter = option; trigger.setMessage(regionLabel()); rebuildGrid(); root.removeChild(dropdown);
            });
        });
    }

    private boolean matchesFilters(LfgProtocol.Lobby lobby) {
        return statusFilter.matches(lobby) && raidFilter.matches(lobby) && regionFilter.matches(lobby);
    }

    private Component statusLabel() { return RalleTheme.dropdownLabel(Component.literal("Status: " + statusFilter.label)); }
    private Component raidLabel() { return RalleTheme.dropdownLabel(Component.literal("Raid: " + raidFilter.label)); }
    private Component regionLabel() { return RalleTheme.dropdownLabel(Component.literal("Region: " + regionFilter.label)); }

    private Color regionColor(LfgProtocol.Region region) {
        if (region == currentRegion) return REGION_GOOD;
        return switch (currentRegion) {
            case EU -> region == LfgProtocol.Region.NA ? REGION_MODERATE : REGION_POOR;
            case NA -> region == LfgProtocol.Region.EU ? REGION_MODERATE : REGION_POOR;
            case AS -> region == LfgProtocol.Region.EU ? REGION_MODERATE : REGION_POOR;
        };
    }

    private String statusText(RaidLfgService.LifecycleState state) {
        return switch (state) {
            case ONLINE -> "Live".equals(service.statusMessage())
                    ? "● Live · synchronized with Fox"
                    : "Host action failed · " + service.statusMessage();
            case AUTHENTICATING -> "Authenticating...";
            case SYNCING -> "Synchronizing...";
            case RECONNECTING -> "Offline · reconnecting";
            case OUTDATED -> "Update required";
            case INELIGIBLE -> "Alliance access unavailable";
            case DISABLED -> "Disabled";
            case NOT_ON_WYNNCRAFT -> "Not connected to Wynncraft";
            case UNAVAILABLE -> "Unavailable";
        };
    }

    private static String disabledJoinLabel(LfgProtocol.Lobby lobby) {
        if (lobby.locked()) return "Locked";
        if (lobby.status() == LfgProtocol.LobbyStatus.IN_RAID) return "In Raid";
        return "Join";
    }

    private static String capabilityReason(String reason) {
        return switch (reason) {
            case "ALREADY_JOINED" -> "You are already in this lobby.";
            case "PLAYER_ALREADY_ACTIVE" -> "You are already active in another lobby.";
            case "LOBBY_LOCKED" -> "This lobby is locked.";
            case "LOBBY_FULL" -> "This lobby is full or already in raid.";
            case "HOST_MUST_DISBAND" -> "Hosts disband instead of leaving.";
            case "NOT_A_MEMBER" -> "You are not a member of this lobby.";
            default -> reason.replace('_', ' ').toLowerCase(Locale.ROOT);
        };
    }

    private static net.minecraft.world.item.Item raidIcon(LfgProtocol.RaidType raid) {
        return switch (raid) {
            case DAILIES -> Items.BUNDLE;
            case NOTG -> Items.SALMON;
            case NOL -> Items.OAK_SAPLING;
            case TCC -> Items.CLAY_BALL;
            case TNA -> Items.ENDER_PEARL;
            case TWP -> Items.FIRE_CHARGE;
        };
    }

    private static String raidName(LfgProtocol.RaidType raid) {
        return switch (raid) {
            case DAILIES -> "Dailies";
            case NOTG -> "Nest of the Grootslangs";
            case NOL -> "Orphion's Nexus of Light";
            case TCC -> "The Canyon Colossus";
            case TNA -> "The Nameless Anomaly";
            case TWP -> "The Wartorn Palace";
        };
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    @Override
    public void onClose() {
        joinCountdown.cancel();
        kickTargeting.reset();
        minecraft.setScreen(parent);
    }

    record CardAction(CardActionKind kind, String label, boolean destructive, boolean requiresConfirmation) {}

    enum CardActionKind {
        JOIN("join"), LEAVE("leave"), DISBAND("disband");

        private final String protocolAction;

        CardActionKind(String protocolAction) {
            this.protocolAction = protocolAction;
        }
    }

    private enum StatusFilter {
        OPEN("Open"), IN_RAID("In Raid"), ALL("All");
        private final String label;
        StatusFilter(String label) { this.label = label; }
        boolean matches(LfgProtocol.Lobby lobby) {
            return this == ALL || this == OPEN && lobby.status() == LfgProtocol.LobbyStatus.OPEN
                    || this == IN_RAID && lobby.status() == LfgProtocol.LobbyStatus.IN_RAID;
        }
    }

    private enum RaidFilter {
        ALL("All", null), DAILIES("Dailies", LfgProtocol.RaidType.DAILIES),
        NOTG("Nest of the Grootslangs", LfgProtocol.RaidType.NOTG),
        NOL("Orphion's Nexus of Light", LfgProtocol.RaidType.NOL),
        TCC("The Canyon Colossus", LfgProtocol.RaidType.TCC),
        TNA("The Nameless Anomaly", LfgProtocol.RaidType.TNA),
        TWP("The Wartorn Palace", LfgProtocol.RaidType.TWP);
        private final String label;
        private final LfgProtocol.RaidType raid;
        RaidFilter(String label, LfgProtocol.RaidType raid) { this.label = label; this.raid = raid; }
        boolean matches(LfgProtocol.Lobby lobby) { return this == ALL || raid == lobby.raidType(); }
    }

    private enum RegionFilter {
        ALL("All"), NA("NA"), EU("EU"), AS("AS");
        private final String label;
        RegionFilter(String label) { this.label = label; }
        boolean matches(LfgProtocol.Lobby lobby) { return this == ALL || name().equals(lobby.region().name()); }
    }
}
