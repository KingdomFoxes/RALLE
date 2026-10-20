package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.classes.*;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ordered, internally scrolling shortcut editor. No import/export or server character mutations. */
final class ClassShortcutsPanel extends FlowLayout {
    private final RalleSettingsScreen screen;
    private final ClassSelectorStore store;
    private final SettingsRegistry settings;
    private final ExpandableParentChildListPanel rows;
    private final RalleScrollContainer scroll;
    private final io.wispforest.owo.ui.component.LabelComponent status;
    private UUID renderedAccount;
    private long renderedRevision = -1;
    private boolean refreshPending;
    private List<String> renderedReserved = List.of();
    private String renderedModifier = "";
    private ShortcutRow dragged;
    private int dragSource;
    private final List<UIComponent> displayedRows = new ArrayList<>();

    ClassShortcutsPanel(RalleSettingsScreen screen, ClassSelectorStore store, SettingsRegistry settings) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.screen = screen;
        this.store = store;
        this.settings = settings;
        gap(4).padding(Insets.of(5)).surface(RalleSurfaces.NAVY_ROW);
        rows = new ExpandableParentChildListPanel(Sizing.fill(100), Sizing.content());
        rows.gap(2);
        var columns = new SettingsListEditorColumns(rows);
        scroll = columns.scroll();
        columns.actions().child(button("ralle.classes.add", this::picker));
        var reset = button("ralle.consumables.action.reset", () -> {
            var owner = account();
            RalleModalDialogs.confirm(screen, Component.translatable("ralle.classes.reset.title"),
                    Component.translatable("ralle.classes.reset.message"),
                    Component.translatable("ralle.consumables.action.reset"), true,
                    () -> mutate(owner, () -> store.clearShortcuts(owner)));
        });
        reset.renderer(RalleButtonRenderers.destructive());
        columns.actions().child(reset);
        status = UIComponents.label(Component.empty()).lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.muted()).maxWidth(SettingsListEditorColumns.ACTION_WIDTH - 8);
        columns.actions().child(status);
        child(columns);
        rebuild();
    }

    private static UUID account() { return Minecraft.getInstance().getUser().getProfileId(); }
    private static Component text(String key, Object... args) { return RalleTheme.ui(Component.translatable(key, args)); }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (dragged != null) {
            var client = Minecraft.getInstance();
            if (!client.isWindowActive() || !account().equals(renderedAccount) || renderedRevision != store.revision()) {
                cancelDrag();
            } else if (GLFW.glfwGetMouseButton(client.getWindow().handle(), 0) != GLFW.GLFW_PRESS) {
                drop(mouseX, mouseY);
            } else {
                if (mouseX >= scroll.x() && mouseX < scroll.x() + scroll.width()) {
                    if (mouseY < scroll.y() + 14) scroll.scrollToOffsetImmediately(scroll.offset() - delta * 4.5);
                    else if (mouseY > scroll.y() + scroll.height() - 14)
                        scroll.scrollToOffsetImmediately(scroll.offset() + delta * 4.5);
                }
                previewDrop(mouseY);
            }
        }
        if (!refreshPending && (!account().equals(renderedAccount) || renderedRevision != store.revision()
                || !reserved().equals(renderedReserved) || !modifier().equals(renderedModifier))) {
            refreshPending = true;
            Minecraft.getInstance().schedule(() -> { refreshPending = false; rebuild(); });
        }
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
    }

    private String modifier() { return settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class).value(); }

    private List<String> reserved() {
        return ClassBindingConflicts.reserved(settings);
    }

    private void rebuild() {
        dragged = null;
        renderedAccount = account();
        renderedRevision = store.revision();
        renderedReserved = reserved();
        renderedModifier = modifier();
        var snapshot = store.account(renderedAccount);
        var content = new ArrayList<UIComponent>();
        if (snapshot.shortcuts().isEmpty()) {
            var empty = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(198));
            empty.horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER);
            empty.child(UIComponents.label(text("ralle.classes.empty")).color(RalleTheme.muted()));
            content.add(empty);
        }
        for (var shortcut : snapshot.shortcuts()) {
            var character = snapshot.character(shortcut.characterId());
            String name = character == null ? shortcut.name().isBlank()
                    ? Component.translatable("ralle.classes.missing").getString() : shortcut.name()
                    : character.displayName(shortcut.name());
            var row = new ShortcutRow(shortcut.characterId());
            row.gap(3).padding(Insets.of(2)).verticalAlignment(VerticalAlignment.CENTER).surface(RalleSurfaces.NAVY_ROW);
            if (character == null) row.tooltip(text("ralle.classes.missing-hint"));
            row.child(UIComponents.item(ClassPresentation.item(character)).sizing(Sizing.fixed(18)));
            var label = UIComponents.label(RalleTheme.ui(Component.literal(name))).maxWidth(120);
            label.horizontalSizing(Sizing.expand(100));
            label.color(character == null ? RalleTheme.muted() : RalleTheme.text());
            row.child(label);
            boolean conflict = ClassSelectorInput.conflict(shortcut.binding(),
                    renderedModifier, snapshot.shortcuts(), renderedReserved);
            var binding = UIComponents.label(conflict ? text("ralle.classes.conflict") : bindingLabel(shortcut.binding()));
            binding.color(conflict ? Color.ofRgb(0xFF6B6B) : RalleTheme.muted()).maxWidth(70);
            row.child(binding);
            var owner = renderedAccount;
            var edit = UIComponents.button(text("ralle.classes.edit"), ignored -> edit(owner, shortcut));
            edit.sizing(Sizing.fixed(40), Sizing.fixed(20));
            edit.renderer(RalleButtonRenderers.neutral());
            row.child(edit);
            row.child(RalleIconButtons.destructiveX(text("ralle.classes.remove", name), ignored -> mutate(owner,
                    () -> store.remove(owner, shortcut.characterId()))));
            content.add(row);
        }
        rows.replaceChildren(content);
        displayedRows.clear();
        displayedRows.addAll(content);
        status.text(!store.available() ? text("ralle.classes.save-failed")
                : ClassBindingConflicts.modifier(settings) ? text("ralle.classes.modifier-conflict")
                : snapshot.characters().isEmpty() ? text("ralle.classes.detect-next")
                : Component.empty());
    }

    private void previewDrop(double mouseY) {
        if (dragged == null) return;
        int destination = 0;
        for (var row : displayedRows)
            if (row != dragged && mouseY > row.y() + row.height() / 2d) destination++;
        if (destination == displayedRows.indexOf(dragged)) return;
        displayedRows.remove(dragged);
        displayedRows.add(destination, dragged);
        rows.replaceChildren(displayedRows);
    }

    private void drop(double mouseX, double mouseY) {
        if (dragged == null) return;
        if (!Minecraft.getInstance().isWindowActive() || !account().equals(renderedAccount) || renderedRevision != store.revision()
                || !scroll.isInBoundingBox(mouseX, mouseY)) { cancelDrag(); return; }
        previewDrop(mouseY);
        var id = dragged.characterId;
        int destination = displayedRows.indexOf(dragged);
        dragged = null;
        if (destination != dragSource) {
            if (!mutate(renderedAccount, () -> store.move(renderedAccount, id, destination))) {
                rebuild();
                status.text(text("ralle.classes.save-failed"));
            }
        } else rebuild();
    }

    private void cancelDrag() { dragged = null; rebuild(); }

    /** Keep the row's measured bounds and focus while rendering only its drop placeholder. */
    private final class ShortcutRow extends FlowLayout {
        private final String characterId;
        ShortcutRow(String characterId) {
            super(Sizing.fill(100), Sizing.content(), Algorithm.HORIZONTAL);
            this.characterId = characterId;
        }
        @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            if (dragged == this) graphics.fill(x, y, x + width, y + height, 0xBFFFFFFF);
            else super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }
        @Override public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            if (super.onMouseDown(click, doubled)) return true; // Edit/remove retain their normal clicks.
            if (click.button() != 0) return false;
            dragged = this;
            dragSource = displayedRows.indexOf(this);
            focusHandler().focus(this, FocusSource.MOUSE_CLICK);
            return true;
        }
        @Override public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
            if (click.button() != 0 || dragged != this) return false;
            previewDrop(y + click.y());
            return true;
        }
        @Override public boolean onMouseUp(MouseButtonEvent click) {
            if (click.button() != 0 || dragged != this) return false;
            drop(x + click.x(), y + click.y());
            return true;
        }
        @Override public boolean canFocus(FocusSource source) { return source == FocusSource.MOUSE_CLICK; }
    }

    private void picker() {
        picker(account(), null, () -> {});
    }
    private void picker(UUID owner, ClassShortcut replacement, Runnable replaced) {
        var snapshot = store.account(owner);
        var content = modal("ralle.classes.add");
        var choices = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        @SuppressWarnings("rawtypes") var overlay = new OverlayContainer[1];
        int count = 0;
        for (var character : snapshot.characters()) {
            if (snapshot.shortcuts().stream().anyMatch(s -> s.characterId().equals(character.id()))) continue;
            count++;
            var button = UIComponents.button(RalleTheme.ui(Component.literal(character.displayName(""))), ignored -> {
                if (mutate(owner, () -> {
                    if (replacement == null) store.add(owner, character.id());
                    else store.retarget(owner, replacement, character.id());
                })) { overlay[0].remove(); replaced.run(); }
            });
            button.sizing(Sizing.fill(100), Sizing.fixed(20));
            button.renderer(RalleButtonRenderers.neutral());
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(4);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(UIComponents.item(ClassPresentation.item(character)).sizing(Sizing.fixed(18)));
            button.horizontalSizing(Sizing.expand(100));
            row.child(button);
            choices.child(row);
        }
        if (count == 0) choices.child(UIComponents.label(text(snapshot.characters().isEmpty()
                ? "ralle.classes.detect-next" : "ralle.classes.all-added")).color(RalleTheme.muted()).maxWidth(266));
        content.child(new RalleScrollContainer(Sizing.fill(100), Sizing.fixed(150), choices).wheelStep(24));
        overlay[0] = screen.showModal(content);
    }

    private void edit(UUID owner, ClassShortcut shortcut) {
        var content = modal("ralle.classes.edit.title");
        content.child(UIComponents.label(text("ralle.classes.name")).color(RalleTheme.muted()));
        var name = UIComponents.textBox(Sizing.fill(100));
        name.setMaxLength(64);
        name.setValue(shortcut.name());
        content.child(name);
        content.child(UIComponents.label(text("ralle.classes.option-key")).color(RalleTheme.muted()));
        String[] draftBinding = {shortcut.binding()};
        // Horizontal binding + optional X share the exact width of Apply.
        var bindingControls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20)).gap(4);
        var binding = UIComponents.button(bindingLabel(draftBinding[0]), ignored -> {});
        var clear = RalleIconButtons.destructiveX(text("ralle.settings.keybind.reset"), ignored -> {
            draftBinding[0] = ClassShortcut.UNBOUND;
            binding.setMessage(bindingLabel(draftBinding[0]));
            bindingControls.removeChild(ignored);
        });
        binding.onPress(ignored -> {
            binding.setMessage(text("ralle.settings.keybind.press-key"));
            screen.captureOptionKey(value -> {
                draftBinding[0] = value;
                binding.setMessage(bindingLabel(value));
                boolean bound = !ClassShortcut.UNBOUND.equals(value);
                if (bound && !bindingControls.children().contains(clear)) bindingControls.child(clear);
                else if (!bound && bindingControls.children().contains(clear)) bindingControls.removeChild(clear);
            });
        });
        binding.sizing(Sizing.expand(100), Sizing.fixed(20));
        binding.renderer(RalleButtonRenderers.neutral());
        bindingControls.child(binding);
        if (!ClassShortcut.UNBOUND.equals(draftBinding[0])) bindingControls.child(clear);
        content.child(bindingControls);
        var error = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFF6B6B)).maxWidth(266);
        content.child(error);
        @SuppressWarnings("rawtypes") var overlay = new OverlayContainer[1];
        if (store.account(owner).character(shortcut.characterId()) == null)
            content.child(button("ralle.classes.retarget", () -> picker(owner,
                    new ClassShortcut(shortcut.characterId(), name.getValue(), draftBinding[0]), () -> overlay[0].remove())));
        content.child(button("ralle.classes.apply", () -> {
            try {
                requireAccount(owner);
                store.edit(owner, new ClassShortcut(shortcut.characterId(), name.getValue(), draftBinding[0]));
                overlay[0].remove();
                rebuild();
            } catch (IOException | IllegalArgumentException failure) { error.text(text("ralle.classes.save-failed")); }
        }));
        overlay[0] = screen.showModal(content);
    }

    private static Component bindingLabel(String binding) {
        if (ClassShortcut.UNBOUND.equals(binding)) return text("ralle.settings.keybind.unbound");
        try { return RalleTheme.ui(InputConstants.getKey(binding).getDisplayName()); }
        catch (IllegalArgumentException invalid) { return text("ralle.settings.keybind.unbound"); }
    }
    private static FlowLayout modal(String title) {
        var content = UIContainers.verticalFlow(Sizing.fixed(290), Sizing.content());
        content.gap(7).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(text(title)).color(RalleTheme.accent()));
        return content;
    }
    private static ButtonComponent button(String key, Runnable action) {
        var button = UIComponents.button(text(key), ignored -> action.run());
        button.sizing(Sizing.fill(100), Sizing.fixed(20));
        button.renderer(RalleButtonRenderers.primary());
        return button;
    }
    @FunctionalInterface private interface Mutation { void run() throws IOException; }
    private static void requireAccount(UUID expected) {
        if (!account().equals(expected)) throw new IllegalArgumentException("Account changed");
    }
    private boolean mutate(UUID owner, Mutation mutation) {
        try {
            requireAccount(owner);
            mutation.run();
            rebuild();
            return true;
        } catch (IOException | IllegalArgumentException failure) {
            status.text(text("ralle.classes.save-failed"));
            return false;
        }
    }
}
