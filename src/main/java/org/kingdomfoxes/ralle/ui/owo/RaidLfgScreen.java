package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.kingdomfoxes.ralle.lfg.client.FakeRaidLobbies;
import org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby;

import java.util.List;
import java.util.Locale;

/**
 * An intentionally disconnected owo-lib prototype for evaluating the Raid LFG browser layout.
 */
public final class RaidLfgScreen extends BaseOwoScreen<FlowLayout> {
    private static final int PANEL_WIDTH = 620;
    private static final int GRID_WIDTH = 584;
    private static final int CARD_WIDTH = 286;
    private static final int HEADER_HEIGHT = 41;
    private static final Identifier FOX_EMBLEM = Identifier.fromNamespaceAndPath("ralle", "textures/gui/fox_overlay.png");
    private static final FontDescription KARLA_BOLD = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("ralle", "karla_bold")
    );
    private static final FontDescription KARLA_BOLD_UI = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("ralle", "karla_bold_ui")
    );
    private static final Color MUTED = Color.ofRgb(0xA9B0BE);
    private static final Color ACCENT = Color.ofRgb(0xF2B84B);
    private static final Color OPEN = Color.ofRgb(0x67D391);
    private static final Color LOCKED = Color.ofRgb(0xFFCA65);
    private static final Color IN_RAID = Color.ofRgb(0x72B7FF);
    private static final Color REGION_GOOD = Color.ofRgb(0x00FF55);
    private static final Color REGION_MODERATE = Color.ofRgb(0xFFFF00);
    private static final Color REGION_POOR = Color.ofRgb(0xFF3333);
    private final Screen parent;
    private final List<FakeRaidLobby> lobbies = FakeRaidLobbies.all();
    private StatusFilter statusFilter = StatusFilter.OPEN;
    private RaidFilter raidFilter = RaidFilter.ALL;
    private RegionFilter regionFilter = RegionFilter.ALL;
    private final FakeRaidLobby.Region currentRegion;
    private FlowLayout gridHost;
    private boolean refreshGridNextTick;

    public RaidLfgScreen(Screen parent) {
        this(parent, FakeRaidLobby.Region.EU);
    }

    public RaidLfgScreen(Screen parent, FakeRaidLobby.Region currentRegion) {
        this.parent = parent;
        this.currentRegion = currentRegion;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.fill(92));
        panel.gap(8).padding(Insets.of(12)).surface(
                Surface.flat(0xFF041330).and(Surface.outline(0xFFFFFFFF))
        );

        panel.child(header());

        var filters = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        filters.gap(6).padding(Insets.left(4)).verticalAlignment(VerticalAlignment.CENTER);

        this.gridHost = UIContainers.verticalFlow(Sizing.fixed(GRID_WIDTH), Sizing.content());
        var create = UIComponents.button(Component.literal("+"), ignored -> {});
        create.sizing(Sizing.fixed(30), Sizing.fixed(20));
        create.renderer(RalleButtonRenderers.primary());
        create.tooltip(Component.literal("Create party (prototype only)"));

        var statusButton = UIComponents.button(statusLabel(), ignored -> {});
        var raidButton = UIComponents.button(raidLabel(), ignored -> {});
        var regionButton = UIComponents.button(regionLabel(), ignored -> {});

        statusButton.renderer(RalleButtonRenderers.selectable(() -> statusFilter != StatusFilter.OPEN));
        raidButton.renderer(RalleButtonRenderers.selectable(() -> raidFilter != RaidFilter.ALL));
        regionButton.renderer(RalleButtonRenderers.selectable(() -> regionFilter != RegionFilter.ALL));
        statusButton.horizontalSizing(Sizing.fixed(164));
        raidButton.horizontalSizing(Sizing.fixed(164));
        regionButton.horizontalSizing(Sizing.fixed(164));

        statusButton.onPress(button -> openStatusDropdown(root, button, this.gridHost));
        raidButton.onPress(button -> openRaidDropdown(root, button, this.gridHost));
        regionButton.onPress(button -> openRegionDropdown(root, button, this.gridHost));

        var refresh = UIComponents.button(Component.empty(), ignored -> refreshGrid());
        refresh.sizing(Sizing.fixed(30), Sizing.fixed(20));
        refresh.renderer(RalleButtonRenderers.refresh());
        refresh.tooltip(Component.literal("Refresh available parties"));

        filters.child(create).child(statusButton).child(raidButton).child(regionButton).child(refresh);
        panel.child(filters);

        rebuildGrid(this.gridHost);
        var scroll = UIContainers.verticalScroll(Sizing.fixed(GRID_WIDTH + 8), Sizing.fill(100), this.gridHost);
        scroll.scrollbarThiccness(4).scrollStep(36);
        panel.child(scroll);

        var footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.verticalAlignment(VerticalAlignment.CENTER);
        footer.child(UIComponents.label(Component.literal("● Live preview · fake party data"))
                .color(OPEN).margins(Insets.right(8)));
        footer.child(UIComponents.label(Component.literal("Use the filters to stress-test the grid."))
                .color(MUTED));
        var close = UIComponents.button(karlaUi(Component.translatable("gui.done")), ignored -> onClose());
        close.renderer(RalleButtonRenderers.neutral());
        close.horizontalSizing(Sizing.fixed(92)).margins(Insets.left(16));
        footer.child(close);
        panel.child(footer);

        root.child(panel);
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshGridNextTick && gridHost != null) {
            refreshGridNextTick = false;
            rebuildGrid(gridHost);
        }
    }

    private void refreshGrid() {
        if (gridHost == null || refreshGridNextTick) return;

        gridHost.clearChildren();
        refreshGridNextTick = true;
    }

    private FlowLayout header() {
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        header.verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.flat(0xFF041330).and(Surface.outline(0xFFFFFFFF)));

        var identity = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        identity.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.left(10));

        var emblem = UIComponents.texture(FOX_EMBLEM, 0, 0, 159, 232, 159, 232).blend(true);
        emblem.sizing(Sizing.fixed(19), Sizing.fixed(28)).margins(Insets.right(10));
        identity.child(emblem);

        var title = Component.literal("ALLY GRAID FINDER")
                .withStyle(style -> style.withFont(KARLA_BOLD));
        identity.child(UIComponents.label(title).lineHeight(16).shadow(false).color(Color.WHITE));

        header.child(identity);
        header.child(UIComponents.spacer());
        header.child(new HeaderAccentComponent(50, HEADER_HEIGHT));
        return header;
    }

    private void openStatusDropdown(FlowLayout root, ButtonComponent trigger, FlowLayout gridHost) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                trigger.x(), trigger.y() + trigger.height(), menu -> {
                    for (var option : StatusFilter.values()) {
                        menu.button(karlaUi(Component.literal(option.label)), dropdown -> {
                            statusFilter = option;
                            trigger.setMessage(statusLabel());
                            rebuildGrid(gridHost);
                            root.removeChild(dropdown);
                        });
                    }
                });
    }

    private void openRaidDropdown(FlowLayout root, ButtonComponent trigger, FlowLayout gridHost) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                trigger.x(), trigger.y() + trigger.height(), menu -> {
                    for (var option : RaidFilter.values()) {
                        menu.button(karlaUi(Component.literal(option.label)), dropdown -> {
                            raidFilter = option;
                            trigger.setMessage(raidLabel());
                            rebuildGrid(gridHost);
                            root.removeChild(dropdown);
                        });
                    }
                });
    }

    private void openRegionDropdown(FlowLayout root, ButtonComponent trigger, FlowLayout gridHost) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                trigger.x(), trigger.y() + trigger.height(), menu -> {
                    for (var option : RegionFilter.values()) {
                        menu.button(karlaUi(Component.literal(option.label)), dropdown -> {
                            regionFilter = option;
                            trigger.setMessage(regionLabel());
                            rebuildGrid(gridHost);
                            root.removeChild(dropdown);
                        });
                    }
                });
    }

    private void rebuildGrid(FlowLayout gridHost) {
        gridHost.clearChildren();
        var visible = lobbies.stream().filter(this::matchesFilters).toList();
        if (visible.isEmpty()) {
            var empty = UIContainers.verticalFlow(Sizing.fixed(GRID_WIDTH), Sizing.fixed(100));
            empty.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER)
                    .surface(Surface.PANEL);
            empty.child(UIComponents.label(karlaUi(Component.literal("No parties match these filters."))).color(MUTED));
            gridHost.child(empty);
            return;
        }

        int rows = (visible.size() + 1) / 2;
        GridLayout grid = UIContainers.grid(Sizing.fixed(GRID_WIDTH), Sizing.content(), rows, 2);
        grid.padding(Insets.of(2));
        for (int i = 0; i < visible.size(); i++) {
            grid.child(lobbyCard(visible.get(i)), i / 2, i % 2);
        }
        gridHost.child(grid);
    }

    private FlowLayout lobbyCard(FakeRaidLobby lobby) {
        var card = UIContainers.verticalFlow(Sizing.fixed(CARD_WIDTH), Sizing.content());
        card.gap(5).padding(Insets.of(9))
                .surface(Surface.flat(0xD91A1E27).and(Surface.outline(0xFF3B4354)));
        card.margins(Insets.of(3));

        var title = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        title.verticalAlignment(VerticalAlignment.CENTER);
        var raidDetails = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        raidDetails.verticalAlignment(VerticalAlignment.CENTER);
        raidDetails.child(UIComponents.item(new ItemStack(raidIcon(lobby.raid())))
                .showOverlay(false).margins(Insets.right(6)));
        raidDetails.child(UIComponents.label(karlaUi(Component.literal(raidName(lobby.raid())))).shadow(true).color(ACCENT));
        raidDetails.child(UIComponents.label(karlaUi(Component.literal("  " + lobby.region())))
                .color(regionColor(lobby.region())));
        title.child(raidDetails);
        var titleSpacer = UIComponents.spacer();
        titleSpacer.verticalSizing(Sizing.fixed(0));
        title.child(titleSpacer);
        title.child(UIComponents.label(karlaUi(Component.literal(statusText(lobby)))).color(statusColor(lobby)));
        card.child(title);

        card.child(UIComponents.label(karlaUi(Component.literal(lobby.note()))).color(MUTED).maxWidth(CARD_WIDTH - 18));

        var roster = UIContainers.grid(Sizing.fill(100), Sizing.content(), 2, 2);
        for (int slot = 0; slot < 4; slot++) {
            roster.child(rosterSlot(lobby, slot), slot / 2, slot % 2);
        }
        card.child(roster);

        var action = UIComponents.button(actionText(lobby), ignored -> {});
        action.horizontalSizing(Sizing.fill(100));
        action.renderer(RalleButtonRenderers.primary());
        action.active = !lobby.locked() && lobby.status() == FakeRaidLobby.Status.OPEN && lobby.members().size() < 4;
        action.tooltip(Component.literal("Prototype only - roster mutations are not connected yet"));
        card.child(action);
        return card;
    }

    private FlowLayout rosterSlot(FakeRaidLobby lobby, int slot) {
        var row = UIContainers.horizontalFlow(Sizing.fixed(130), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER).padding(Insets.of(2));

        if (slot >= lobby.members().size()) {
            row.child(UIComponents.item(new ItemStack(Items.GRAY_STAINED_GLASS_PANE))
                    .showOverlay(false).margins(Insets.right(4)));
            row.child(UIComponents.label(karlaUi(Component.literal("Open slot"))).color(MUTED));
            return row;
        }

        var member = lobby.members().get(slot);
        row.child(new PlayerFaceComponent(member.uuid(), member.ign(), 16).margins(Insets.right(4)));
        var memberLabel = Component.empty();
        if (member.host()) {
            memberLabel.append(Component.literal("★ "));
        }
        memberLabel.append(karlaUi(Component.literal(member.ign() + " [" + member.guild() + "]")));
        row.child(UIComponents.label(memberLabel)
                .color(member.host() ? ACCENT : Color.WHITE));
        return row;
    }

    private boolean matchesFilters(FakeRaidLobby lobby) {
        return statusFilter.matches(lobby)
                && raidFilter.matches(lobby)
                && regionFilter.matches(lobby);
    }

    private Component statusLabel() { return filterLabel("Status: " + statusFilter.label); }
    private Component raidLabel() { return filterLabel("Raid: " + raidFilter.label); }
    private Component regionLabel() { return filterLabel("Region: " + regionFilter.label); }

    private Component filterLabel(String text) {
        return Component.empty()
                .append(karlaUi(Component.literal(text + " ")))
                .append(Component.literal("▾"));
    }

    private Component karlaUi(Component component) {
        return component.copy().withStyle(style -> style.withFont(KARLA_BOLD_UI));
    }

    private String statusText(FakeRaidLobby lobby) {
        if (lobby.locked()) return "LOCKED";
        return lobby.status() == FakeRaidLobby.Status.IN_RAID ? "IN RAID" : lobby.members().size() + "/4";
    }

    private Color statusColor(FakeRaidLobby lobby) {
        if (lobby.locked()) return LOCKED;
        return lobby.status() == FakeRaidLobby.Status.IN_RAID ? IN_RAID : OPEN;
    }

    private Color regionColor(FakeRaidLobby.Region lobbyRegion) {
        if (lobbyRegion == currentRegion) return REGION_GOOD;

        return switch (currentRegion) {
            case EU -> lobbyRegion == FakeRaidLobby.Region.NA ? REGION_MODERATE : REGION_POOR;
            case NA -> lobbyRegion == FakeRaidLobby.Region.EU ? REGION_MODERATE : REGION_POOR;
            case AS -> lobbyRegion == FakeRaidLobby.Region.EU ? REGION_MODERATE : REGION_POOR;
        };
    }

    private Component actionText(FakeRaidLobby lobby) {
        if (lobby.locked()) return karlaUi(Component.literal("Locked"));
        if (lobby.status() == FakeRaidLobby.Status.IN_RAID) return karlaUi(Component.literal("In Raid"));
        return karlaUi(Component.literal("Join Party"));
    }

    private net.minecraft.world.item.Item raidIcon(String raid) {
        return switch (raid) {
            case "Dailies" -> Items.BUNDLE;
            case "NOTG" -> Items.SALMON;
            case "NOL" -> Items.OAK_SAPLING;
            case "TCC" -> Items.CLAY_BALL;
            case "TNA" -> Items.ENDER_PEARL;
            case "TWP" -> Items.FIRE_CHARGE;
            default -> Items.NETHER_STAR;
        };
    }

    private String raidName(String raid) {
        return switch (raid) {
            case "NOTG" -> "Nest of the Grootslangs";
            case "NOL" -> "Orphion's Nexus of Light";
            case "TCC" -> "The Canyon Colossus";
            case "TNA" -> "The Nameless Anomaly";
            case "TWP" -> "The Wartorn Palace";
            default -> raid;
        };
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private enum StatusFilter {
        OPEN("Open"), IN_RAID("In Raid"), ALL("All");
        private final String label;
        StatusFilter(String label) { this.label = label; }
        boolean matches(FakeRaidLobby lobby) {
            return this == ALL || (this == OPEN && lobby.status() == FakeRaidLobby.Status.OPEN)
                    || (this == IN_RAID && lobby.status() == FakeRaidLobby.Status.IN_RAID);
        }
    }

    private enum RaidFilter {
        ALL("All", null),
        DAILIES("Dailies", "Dailies"),
        NOTG("Nest of the Grootslangs", "NOTG"),
        NOL("Orphion's Nexus of Light", "NOL"),
        TCC("The Canyon Colossus", "TCC"),
        TNA("The Nameless Anomaly", "TNA"),
        TWP("The Wartorn Palace", "TWP");
        private final String label;
        private final String raidId;
        RaidFilter(String label, String raidId) {
            this.label = label;
            this.raidId = raidId;
        }
        boolean matches(FakeRaidLobby lobby) { return this == ALL || raidId.equals(lobby.raid()); }
    }

    private enum RegionFilter {
        ALL("All"), NA("NA"), EU("EU"), AS("AS");
        private final String label;
        RegionFilter(String label) { this.label = label; }
        boolean matches(FakeRaidLobby lobby) { return this == ALL || name().equals(lobby.region().name()); }
    }
}
