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

import java.util.HashSet;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Live Raid LFG browser backed by the persistent protocol-v1 service. */
public final class RaidLfgScreen extends BaseOwoScreen<FlowLayout> {
    private static final int GRID_WIDTH = 584;
    private static final int CARD_WIDTH = 286;
    private static final Color REGION_GOOD = Color.ofRgb(0x00FF55);
    private static final Color REGION_MODERATE = Color.ofRgb(0xFFFF00);
    private static final Color REGION_POOR = Color.ofRgb(0xFF3333);

    private final Screen parent;
    private final RaidLfgService service;
    private final Set<UUID> expandedLobbies = new HashSet<>();
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

    public RaidLfgScreen(Screen parent, RaidLfgService service) {
        this(parent, service, RaidRegionDetector.UNAVAILABLE);
    }

    public RaidLfgScreen(Screen parent, RaidLfgService service, RaidRegionDetector regionDetector) {
        this.parent = parent;
        this.service = service;
        this.currentRegion = regionDetector.detect().orElse(LfgProtocol.Region.EU);
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
        footerStatus.color(online ? RalleTheme.POSITIVE : lifecycle == RaidLfgService.LifecycleState.OUTDATED
                || lifecycle == RaidLfgService.LifecycleState.INELIGIBLE ? RalleTheme.ACCENT : RalleTheme.MUTED);
        rebuildGrid();
    }

    private void rebuildGrid() {
        gridHost.clearChildren();
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) {
            gridHost.child(statePanel(service.statusMessage()));
            return;
        }
        var visible = service.store().state().lobbyList().stream().filter(this::matchesFilters).toList();
        if (visible.isEmpty()) {
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
                .tooltip(RalleTheme.ui(Component.literal(expanded ? "Hide party details" : "Review the roster before joining")));
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
        if (!expanded) summary.child(collapsedAction(lobby));
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
        var action = expandedAction(lobby);
        action.horizontalSizing(Sizing.fill(100));
        card.child(action);
        return card;
    }

    private ButtonComponent collapsedAction(LfgProtocol.Lobby lobby) {
        var action = collapsedActionFor(lobby, viewerId());
        var button = UIComponents.button(RalleTheme.ui(Component.literal(action.label())), ignored -> {
            if (action.requiresConfirmation()) {
                openDisbandConfirmation(lobby);
            } else {
                expandedLobbies.add(lobby.lobbyId());
                rebuildGrid();
            }
        });
        button.sizing(Sizing.fixed(action.destructive() ? 64 : 52), Sizing.fixed(20));
        button.renderer(action.destructive() ? RalleButtonRenderers.destructive() : RalleButtonRenderers.primary());
        button.active = service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && !service.pending(lobby.lobbyId(), action.kind().protocolAction);
        button.tooltip(RalleTheme.ui(Component.literal(action.destructive()
                ? "Disband this party"
                : "Expand and review the roster before joining")));
        return button;
    }

    private ButtonComponent expandedAction(LfgProtocol.Lobby lobby) {
        var viewerId = viewerId();
        var action = expandedActionFor(lobby, viewerId);
        boolean member = viewerId != null && lobby.contains(viewerId);
        boolean allowed = action.kind() == CardActionKind.DISBAND
                || lobby.capabilities() != null && (member ? lobby.capabilities().leave() : lobby.capabilities().join());
        var button = UIComponents.button(RalleTheme.ui(Component.literal(action.label())), ignored -> {
            if (action.requiresConfirmation()) openDisbandConfirmation(lobby);
            else performAction(lobby, action.kind().protocolAction);
        });
        button.sizing(Sizing.fixed(72), Sizing.fixed(20));
        button.renderer(action.destructive() ? RalleButtonRenderers.destructive() : RalleButtonRenderers.primary());
        button.active = allowed && !service.pending(lobby.lobbyId(), action.kind().protocolAction);
        if (!allowed && lobby.capabilities() != null) {
            var reason = lobby.capabilities().reason(member ? "leave" : "join");
            if (reason != null) button.tooltip(RalleTheme.ui(Component.literal(capabilityReason(reason))));
        }
        return button;
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
        return row;
    }

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
        return new CardAction(CardActionKind.REVIEW_JOIN, "Join", false, false);
    }

    static CardAction expandedActionFor(LfgProtocol.Lobby lobby, UUID viewerId) {
        if (viewerId != null && lobby.hostedBy(viewerId)) {
            return new CardAction(CardActionKind.DISBAND, "Disband", true, true);
        }
        if (viewerId != null && lobby.contains(viewerId)) {
            return new CardAction(CardActionKind.LEAVE, "Leave Lobby", true, false);
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

    private static String statusText(RaidLfgService.LifecycleState state) {
        return switch (state) {
            case ONLINE -> "● Live · synchronized with Fox";
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

    @Override public void onClose() { minecraft.setScreen(parent); }

    record CardAction(CardActionKind kind, String label, boolean destructive, boolean requiresConfirmation) {}

    enum CardActionKind {
        REVIEW_JOIN("join"), JOIN("join"), LEAVE("leave"), DISBAND("disband");

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
