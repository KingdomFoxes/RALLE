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
    private static final Color MUTED = Color.ofRgb(0xA9B0BE);
    private static final Color ACCENT = Color.ofRgb(0xF2B84B);
    private static final Color OPEN = Color.ofRgb(0x67D391);
    private static final Color LOCKED = Color.ofRgb(0xFFCA65);
    private static final Color IN_RAID = Color.ofRgb(0x72B7FF);
    private static final int CREATE_BUTTON_COLOR = 0xFF238636;
    private static final int CREATE_BUTTON_HOVERED_COLOR = 0xFF2EA043;
    private static final int CREATE_BUTTON_DISABLED_COLOR = 0xFF39543F;
    private static final int CREATE_BUTTON_BORDER_COLOR = 0xFF0B0D0C;

    private final Screen parent;
    private final List<FakeRaidLobby> lobbies = FakeRaidLobbies.all();
    private StatusFilter statusFilter = StatusFilter.OPEN;
    private RaidFilter raidFilter = RaidFilter.ALL;
    private RegionFilter regionFilter = RegionFilter.ALL;

    public RaidLfgScreen(Screen parent) {
        this.parent = parent;
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
        panel.gap(8).padding(Insets.of(12)).surface(Surface.DARK_PANEL);

        panel.child(header());

        var filters = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        filters.gap(6).padding(Insets.left(4)).verticalAlignment(VerticalAlignment.CENTER);

        var gridHost = UIContainers.verticalFlow(Sizing.fixed(GRID_WIDTH), Sizing.content());
        var create = UIComponents.button(Component.literal("+"), ignored -> {});
        create.sizing(Sizing.fixed(30), Sizing.fixed(20));
        create.renderer(createButtonRenderer());
        create.tooltip(Component.literal("Create party (prototype only)"));

        var statusButton = UIComponents.button(statusLabel(), ignored -> {});
        var raidButton = UIComponents.button(raidLabel(), ignored -> {});
        var regionButton = UIComponents.button(regionLabel(), ignored -> {});

        statusButton.horizontalSizing(Sizing.fixed(176));
        raidButton.horizontalSizing(Sizing.fixed(176));
        regionButton.horizontalSizing(Sizing.fixed(176));

        statusButton.onPress(button -> openStatusDropdown(root, button, gridHost));
        raidButton.onPress(button -> openRaidDropdown(root, button, gridHost));
        regionButton.onPress(button -> openRegionDropdown(root, button, gridHost));

        filters.child(create).child(statusButton).child(raidButton).child(regionButton);
        panel.child(filters);

        rebuildGrid(gridHost);
        var scroll = UIContainers.verticalScroll(Sizing.fixed(GRID_WIDTH + 8), Sizing.fill(100), gridHost);
        scroll.scrollbarThiccness(4).scrollStep(36);
        panel.child(scroll);

        var footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.verticalAlignment(VerticalAlignment.CENTER);
        footer.child(UIComponents.label(Component.literal("● Live preview · fake party data"))
                .color(OPEN).margins(Insets.right(8)));
        footer.child(UIComponents.label(Component.literal("Use the filters to stress-test the grid."))
                .color(MUTED));
        var close = UIComponents.button(Component.translatable("gui.done"), ignored -> onClose());
        close.horizontalSizing(Sizing.fixed(92)).margins(Insets.left(16));
        footer.child(close);
        panel.child(footer);

        root.child(panel);
    }

    private FlowLayout header() {
        var header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.horizontalAlignment(HorizontalAlignment.CENTER)
                .surface(Surface.PANEL).padding(Insets.of(10));

        var identity = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        identity.verticalAlignment(VerticalAlignment.CENTER);
        identity.child(UIComponents.item(new ItemStack(Items.COMPASS))
                .setTooltipFromStack(true).showOverlay(false).margins(Insets.right(8)));

        var labels = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        labels.gap(3);
        labels.child(UIComponents.label(Component.literal("Raid Party Finder")).shadow(true).color(ACCENT));
        labels.child(UIComponents.label(Component.literal("Kingdom of Foxes Alliance")).color(MUTED));
        identity.child(labels);

        header.child(identity);
        return header;
    }

    private ButtonComponent.Renderer createButtonRenderer() {
        return (graphics, button, delta) -> {
            int x = button.getX();
            int y = button.getY();
            int width = button.getWidth();
            int height = button.getHeight();
            int fill = button.active()
                    ? button.isHovered() ? CREATE_BUTTON_HOVERED_COLOR : CREATE_BUTTON_COLOR
                    : CREATE_BUTTON_DISABLED_COLOR;

            graphics.fill(x, y, x + width, y + height, CREATE_BUTTON_BORDER_COLOR);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        };
    }

    private void openStatusDropdown(FlowLayout root, ButtonComponent trigger, FlowLayout gridHost) {
        DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                trigger.x(), trigger.y() + trigger.height(), menu -> {
                    for (var option : StatusFilter.values()) {
                        menu.button(Component.literal(option.label), dropdown -> {
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
                        menu.button(Component.literal(option.label), dropdown -> {
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
                        menu.button(Component.literal(option.label), dropdown -> {
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
            empty.child(UIComponents.label(Component.literal("No parties match these filters.")).color(MUTED));
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
        title.child(UIComponents.item(new ItemStack(raidIcon(lobby.raid())))
                .showOverlay(false).margins(Insets.right(6)));
        title.child(UIComponents.label(Component.literal(lobby.raid())).shadow(true).color(ACCENT));
        title.child(UIComponents.label(Component.literal("  " + lobby.region())).color(MUTED));
        title.child(UIComponents.label(Component.literal("  " + statusText(lobby))).color(statusColor(lobby)));
        card.child(title);

        card.child(UIComponents.label(Component.literal(lobby.note())).color(MUTED).maxWidth(CARD_WIDTH - 18));

        var roster = UIContainers.grid(Sizing.fill(100), Sizing.content(), 2, 2);
        for (int slot = 0; slot < 4; slot++) {
            roster.child(rosterSlot(lobby, slot), slot / 2, slot % 2);
        }
        card.child(roster);

        var action = UIComponents.button(actionText(lobby), ignored -> {});
        action.horizontalSizing(Sizing.fill(100));
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
            row.child(UIComponents.label(Component.literal("Open slot")).color(MUTED));
            return row;
        }

        var member = lobby.members().get(slot);
        row.child(new PlayerFaceComponent(member.uuid(), member.ign(), 16).margins(Insets.right(4)));
        var text = member.host() ? "★ " + member.ign() : member.ign();
        row.child(UIComponents.label(Component.literal(text + " [" + member.guild() + "]"))
                .color(member.host() ? ACCENT : Color.WHITE));
        return row;
    }

    private boolean matchesFilters(FakeRaidLobby lobby) {
        return statusFilter.matches(lobby)
                && raidFilter.matches(lobby)
                && regionFilter.matches(lobby);
    }

    private Component statusLabel() { return Component.literal("Status: " + statusFilter.label + " ▾"); }
    private Component raidLabel() { return Component.literal("Raid: " + raidFilter.label + " ▾"); }
    private Component regionLabel() { return Component.literal("Region: " + regionFilter.label + " ▾"); }

    private String statusText(FakeRaidLobby lobby) {
        if (lobby.locked()) return "LOCKED";
        return lobby.status() == FakeRaidLobby.Status.IN_RAID ? "IN RAID" : lobby.members().size() + "/4";
    }

    private Color statusColor(FakeRaidLobby lobby) {
        if (lobby.locked()) return LOCKED;
        return lobby.status() == FakeRaidLobby.Status.IN_RAID ? IN_RAID : OPEN;
    }

    private Component actionText(FakeRaidLobby lobby) {
        if (lobby.locked()) return Component.literal("Locked");
        if (lobby.status() == FakeRaidLobby.Status.IN_RAID) return Component.literal("In Raid");
        return Component.literal("Join Party");
    }

    private net.minecraft.world.item.Item raidIcon(String raid) {
        return switch (raid) {
            case "Dailies" -> Items.BUNDLE;
            case "NOTG" -> Items.ROTTEN_FLESH;
            case "NOL" -> Items.AMETHYST_SHARD;
            case "TCC" -> Items.IRON_GOLEM_SPAWN_EGG;
            case "TNA" -> Items.ECHO_SHARD;
            case "TWP" -> Items.PRISMARINE_CRYSTALS;
            default -> Items.NETHER_STAR;
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
        ALL("All"), DAILIES("Dailies"), NOTG("NOTG"), NOL("NOL"), TCC("TCC"), TNA("TNA"), TWP("TWP");
        private final String label;
        RaidFilter(String label) { this.label = label; }
        boolean matches(FakeRaidLobby lobby) { return this == ALL || label.equals(lobby.raid()); }
    }

    private enum RegionFilter {
        ALL("All"), NA("NA"), EU("EU"), AS("AS");
        private final String label;
        RegionFilter(String label) { this.label = label; }
        boolean matches(FakeRaidLobby lobby) { return this == ALL || name().equals(lobby.region().name()); }
    }
}
