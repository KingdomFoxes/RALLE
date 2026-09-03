package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightRule;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

/** Framed, internally scrolling ordered rule editor with a fixed right action lane. */
final class ConsumableHighlightsPanel extends FlowLayout {
    private static final int ACTION_LANE_WIDTH = 104;
    private final RalleSettingsScreen screen;
    private final ConsumableHighlightStore store;
    private final ExpandableParentChildListPanel list;
    private final FlowLayout listContent;
    private final io.wispforest.owo.ui.component.LabelComponent status;

    ConsumableHighlightsPanel(RalleSettingsScreen screen, ConsumableHighlightStore store, Set<Integer> expanded) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.screen = screen;
        this.store = store;
        gap(4).padding(Insets.of(5)).surface(RalleSurfaces.NAVY_ROW);

        var columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(230));
        columns.gap(6);
        listContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        listContent.gap(2).padding(Insets.of(4));
        list = new ExpandableParentChildListPanel(Sizing.fill(100), Sizing.content(), expanded);
        list.gap(2);
        listContent.child(list);
        var listScroll = new RalleScrollContainer(Sizing.expand(100), Sizing.fixed(220), listContent);
        listScroll.wheelStep(18).scrollbarThiccness(3).surface(RalleSurfaces.NAVY_PANEL);
        columns.child(listScroll);

        var actions = UIContainers.verticalFlow(Sizing.fixed(ACTION_LANE_WIDTH), Sizing.fixed(220));
        actions.gap(5).padding(Insets.of(4)).surface(RalleSurfaces.NAVY_PANEL);
        status = UIComponents.label(Component.empty()).lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.MUTED).maxWidth(ACTION_LANE_WIDTH - 8);
        actions.child(action("ralle.consumables.action.add", this::openAdd, RalleButtonRenderers.primary()));
        actions.child(action("ralle.consumables.action.import", this::startImport, RalleButtonRenderers.neutral()));
        actions.child(action("ralle.consumables.action.export", this::startExport, RalleButtonRenderers.neutral()));
        actions.child(action("ralle.consumables.action.reset", this::confirmReset, RalleButtonRenderers.destructive()));
        actions.child(status);
        columns.child(actions);
        child(columns);
        rebuild();
    }

    private ButtonComponent action(String key, Runnable pressed, ButtonComponent.Renderer renderer) {
        var button = UIComponents.button(RalleTheme.ui(Component.translatable(key)), ignored -> pressed.run());
        button.sizing(Sizing.fill(100), Sizing.fixed(20));
        button.renderer(renderer);
        return button;
    }

    void rebuild() {
        list.clearChildren();
        var rules = store.snapshot();
        if (rules.isEmpty()) {
            var empty = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(198));
            empty.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER);
            empty.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.consumables.empty")))
                    .color(RalleTheme.MUTED));
            list.child(empty);
            return;
        }
        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            var rule = rules.get(ruleIndex);
            list.child(parentRow(ruleIndex, rule));
            if (!list.expanded(ruleIndex)) continue;
            for (int aliasIndex = 0; aliasIndex < rule.aliases().size(); aliasIndex++) {
                list.child(aliasRow(ruleIndex, aliasIndex, rule));
            }
        }
    }

    private FlowLayout parentRow(int index, ConsumableHighlightRule rule) {
        var row = new HoverActionRow(Sizing.fill(100), Sizing.fixed(24));
        row.verticalAlignment(VerticalAlignment.CENTER).padding(Insets.of(2)).surface(RalleSurfaces.NAVY_ROW);
        var arrow = RalleIconButtons.disclosure(
                Component.translatable("ralle.consumables.access.expand", rule.name()),
                () -> list.expanded(index),
                ignored -> toggle(index));
        row.child(arrow);
        var name = new DynamicRuleLabelComponent(
                Component.literal(rule.name()), rule.style(), 124, false, () -> toggle(index));
        name.horizontalSizing(Sizing.expand(100));
        row.child(name);
        var lane = UIContainers.horizontalFlow(Sizing.fixed(64), Sizing.fixed(20)).gap(2);
        lane.child(RalleIconButtons.palette(Component.translatable("ralle.consumables.access.color", rule.name()), ignored ->
                ConsumableColorDialogScreen.openEdit(screen, store, index)).visibleWhen(row::actionsVisible));
        lane.child(RalleIconButtons.add(Component.translatable("ralle.consumables.access.add-alias", rule.name()), ignored ->
                ConsumableAliasDialogScreen.open(screen, store, index)).visibleWhen(row::actionsVisible));
        lane.child(RalleIconButtons.destructiveX(Component.translatable("ralle.consumables.access.delete", rule.name()), ignored ->
                confirmDeleteRule(index, rule.name())).visibleWhen(row::actionsVisible));
        row.child(lane);
        return row;
    }

    private FlowLayout aliasRow(int ruleIndex, int aliasIndex, ConsumableHighlightRule rule) {
        // Match the parent-row height so the fixed 20px action button is never clipped by padding.
        var row = new HoverActionRow(Sizing.fill(100), Sizing.fixed(24));
        row.verticalAlignment(VerticalAlignment.CENTER).padding(Insets.of(2));
        row.surface((graphics, component) -> {
            int color = rule.style().rainbow()
                    ? org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder.rainbowColor(
                            (component.x() + component.y()) * .01f, System.currentTimeMillis())
                    : 0xFF000000 | rule.style().rgb();
            graphics.fill(component.x() + 11, component.y(), component.x() + 12, component.y() + component.height(),
                    color);
            graphics.fill(component.x() + 11, component.y() + 10, component.x() + 18, component.y() + 11,
                    color);
        });
        var label = new DynamicRuleLabelComponent(Component.literal(rule.aliases().get(aliasIndex)),
                rule.style(), 144, false, false, () -> {});
        label.horizontalSizing(Sizing.expand(100));
        label.margins(Insets.left(20));
        row.child(label);
        var lane = UIContainers.horizontalFlow(Sizing.fixed(64), Sizing.fixed(20));
        lane.horizontalAlignment(HorizontalAlignment.RIGHT);
        lane.child(RalleIconButtons.destructiveX(Component.translatable(
                "ralle.consumables.access.delete", rule.aliases().get(aliasIndex)), ignored -> {
            try {
                store.removeAlias(ruleIndex, aliasIndex);
                clearStatus();
                rebuild();
            } catch (IOException | IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        }).visibleWhen(row::actionsVisible));
        row.child(lane);
        return row;
    }

    private void toggle(int index) {
        list.toggle(index);
        rebuild();
    }

    private void openAdd() {
        ConsumableColorDialogScreen.openAdd(screen, store);
    }

    private void confirmDeleteRule(int index, String name) {
        RalleModalDialogs.confirm(
                screen,
                Component.translatable("ralle.consumables.confirm.delete.title"),
                Component.translatable("ralle.consumables.confirm.delete.message", name),
                Component.translatable("ralle.consumables.action.delete"),
                true,
                () -> {
            try {
                store.removeRule(index);
                list.forgetAtOrAfter(index);
                clearStatus();
                rebuild();
            } catch (IOException | IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
    }

    private void confirmReset() {
        RalleModalDialogs.confirm(
                screen,
                Component.translatable("ralle.consumables.confirm.reset.title"),
                Component.translatable("ralle.consumables.confirm.reset.message"),
                Component.translatable("ralle.consumables.action.reset"),
                true,
                () -> {
            try {
                store.resetDefaults();
                showSuccess("ralle.consumables.status.reset");
                rebuild();
            } catch (IOException exception) {
                showError(exception.getMessage());
            }
        });
    }

    private void startImport() {
        status.text(RalleTheme.ui(Component.translatable("ralle.consumables.status.working")));
        CompletableFuture.supplyAsync(ConsumableHighlightsPanel::openJsonFileDialog)
                .whenComplete((selected, dialogFailure) -> Minecraft.getInstance().schedule(() -> {
            if (dialogFailure != null) {
                showError(failureMessage(dialogFailure));
                return;
            }
            if (selected == null) {
                clearStatus();
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    store.importFile(Path.of(selected));
                    Minecraft.getInstance().schedule(() -> {
                        showSuccess("ralle.consumables.status.imported");
                        rebuild();
                    });
                } catch (IOException | IllegalArgumentException exception) {
                    Minecraft.getInstance().schedule(() -> showError(exception.getMessage()));
                }
            });
        }));
    }

    private void startExport() {
        status.text(RalleTheme.ui(Component.translatable("ralle.consumables.status.working")));
        CompletableFuture.supplyAsync(() -> {
            var selected = saveJsonFileDialog();
            if (selected == null) return null;
            var path = Path.of(selected);
            return new ExportSelection(path, Files.exists(path));
        }).whenComplete((selection, dialogFailure) -> Minecraft.getInstance().schedule(() -> {
            if (dialogFailure != null) {
                showError(failureMessage(dialogFailure));
                return;
            }
            if (selection == null) {
                clearStatus();
                return;
            }
            var destination = selection.path();
            if (selection.exists()) {
                clearStatus();
                RalleModalDialogs.confirm(
                        screen,
                        Component.translatable("ralle.consumables.confirm.overwrite.title"),
                        Component.translatable("ralle.consumables.confirm.overwrite.message"),
                        Component.translatable("ralle.consumables.action.overwrite"),
                        false,
                        () -> exportAsync(destination));
            } else {
                exportAsync(destination);
            }
        }));
    }

    private record ExportSelection(Path path, boolean exists) {}

    private void exportAsync(Path destination) {
        CompletableFuture.runAsync(() -> {
            try {
                store.exportFile(destination);
                Minecraft.getInstance().schedule(() -> showSuccess("ralle.consumables.status.exported"));
            } catch (IOException exception) {
                Minecraft.getInstance().schedule(() -> showError(exception.getMessage()));
            }
        });
    }

    private void showSuccess(String translationKey) {
        status.color(RalleTheme.MUTED).text(RalleTheme.ui(Component.translatable(translationKey)));
    }

    private void clearStatus() {
        status.text(Component.empty());
    }

    private void showError(String message) {
        status.color(Color.ofRgb(0xFF6B6B)).text(RalleTheme.ui(Component.literal(
                message == null || message.isBlank() ? "File operation failed" : message)));
    }

    private static String failureMessage(Throwable failure) {
        var cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "File operation failed" : cause.getMessage();
    }

    private static String openJsonFileDialog() {
        try (var stack = MemoryStack.stackPush()) {
            return TinyFileDialogs.tinyfd_openFileDialog(
                    "Import RALLE consumable highlights",
                    "",
                    stack.pointers(stack.UTF8("*.json")),
                    "JSON files",
                    false
            );
        }
    }

    private static String saveJsonFileDialog() {
        try (var stack = MemoryStack.stackPush()) {
            return TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export RALLE consumable highlights",
                    "ralle-consumable-highlights.json",
                    stack.pointers(stack.UTF8("*.json")),
                    "JSON files"
            );
        }
    }
}
