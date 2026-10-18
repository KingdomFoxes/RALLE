package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.classes.*;
import org.kingdomfoxes.ralle.settings.RalleSettings;

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
    private final io.wispforest.owo.ui.component.LabelComponent status;
    private UUID renderedAccount;
    private long renderedRevision = -1;
    private boolean refreshPending;
    private List<String> renderedReserved = List.of();
    private String renderedModifier = "";

    ClassShortcutsPanel(RalleSettingsScreen screen, ClassSelectorStore store, SettingsRegistry settings) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.screen = screen;
        this.store = store;
        this.settings = settings;
        gap(4).padding(Insets.of(5)).surface(RalleSurfaces.NAVY_ROW);
        rows = new ExpandableParentChildListPanel(Sizing.fill(100), Sizing.content());
        rows.gap(2);
        var columns = new SettingsListEditorColumns(rows);
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
        if (!refreshPending && (!account().equals(renderedAccount) || renderedRevision != store.revision()
                || !reserved().equals(renderedReserved) || !modifier().equals(renderedModifier))) {
            refreshPending = true;
            Minecraft.getInstance().schedule(() -> { refreshPending = false; rebuild(); });
        }
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
    }

    private String modifier() { return settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class).value(); }

    private List<String> reserved() {
        return settings.entries().stream().filter(KeybindSetting.class::isInstance).map(KeybindSetting.class::cast)
                .filter(s -> !s.id().equals(RalleSettings.CLASS_MODIFIER_ID)).map(KeybindSetting::value).toList();
    }

    private void rebuild() {
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
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(3).padding(Insets.of(2)).verticalAlignment(VerticalAlignment.CENTER).surface(RalleSurfaces.NAVY_ROW);
            if (character != null && !character.hasStableIdentity()) row.tooltip(text("ralle.classes.unverified"));
            row.child(UIComponents.item(fallbackIcon(character)).sizing(Sizing.fixed(18)));
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
        status.text(!store.available() ? text("ralle.classes.save-failed")
                : snapshot.characters().isEmpty() ? text("ralle.classes.detect-next")
                : text("ralle.classes.detected", snapshot.characters().size()));
    }

    /** Native item fallback only; a verified game adapter can later supply resource-pack class sprites. */
    private static ItemStack fallbackIcon(ClassCharacter character) {
        if (character == null) return new ItemStack(Items.PAPER);
        String type = character.type().toLowerCase(java.util.Locale.ROOT);
        return new ItemStack(type.contains("archer") || type.contains("hunter") ? Items.BOW
                : type.contains("warrior") || type.contains("knight") ? Items.IRON_SHOVEL
                : type.contains("mage") || type.contains("wizard") ? Items.STICK
                : type.contains("assassin") || type.contains("ninja") ? Items.IRON_SWORD
                : type.contains("shaman") || type.contains("skyseer") ? Items.TOTEM_OF_UNDYING : Items.PAPER);
    }

    private void picker() {
        var owner = account();
        var snapshot = store.account(owner);
        var content = modal("ralle.classes.add");
        var choices = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        @SuppressWarnings("rawtypes") var overlay = new OverlayContainer[1];
        int count = 0;
        for (var character : snapshot.characters()) {
            if (snapshot.shortcuts().stream().anyMatch(s -> s.characterId().equals(character.id()))) continue;
            count++;
            var button = UIComponents.button(RalleTheme.ui(Component.literal(character.displayName(""))), ignored -> {
                if (mutate(owner, () -> store.add(owner, character.id()))) overlay[0].remove();
            });
            // Character numbers distinguish duplicate names without exposing hashes as game identities.
            int number = WynncraftClassMenu.characterNumber(character.id());
            button.tooltip(number < 0 ? RalleTheme.ui(Component.literal(character.type()))
                    : text("ralle.classes.character-number", number, character.type()));
            button.sizing(Sizing.fill(100), Sizing.fixed(20));
            button.renderer(RalleButtonRenderers.neutral());
            choices.child(button);
        }
        if (count == 0) choices.child(UIComponents.label(text(snapshot.characters().isEmpty()
                ? "ralle.classes.detect-next" : "ralle.classes.all-added")).color(RalleTheme.muted()).maxWidth(266));
        content.child(new RalleScrollContainer(Sizing.fill(100), Sizing.fixed(150), choices).wheelStep(24));
        overlay[0] = screen.showModal(content);
    }

    private void edit(UUID owner, ClassShortcut shortcut) {
        var content = modal("ralle.classes.edit");
        content.child(UIComponents.label(text("ralle.classes.name")).color(RalleTheme.muted()));
        var name = UIComponents.textBox(Sizing.fill(100));
        name.setMaxLength(64);
        name.setValue(shortcut.name());
        content.child(name);
        content.child(UIComponents.label(text("ralle.classes.option-key")).color(RalleTheme.muted()));
        String[] draftBinding = {shortcut.binding()};
        var bindingRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        var binding = UIComponents.button(bindingLabel(draftBinding[0]), ignored -> {});
        var clear = RalleIconButtons.destructiveX(text("ralle.settings.keybind.reset"), ignored -> {
            draftBinding[0] = ClassShortcut.UNBOUND;
            binding.setMessage(bindingLabel(draftBinding[0]));
            if (bindingRow.children().getFirst() instanceof ButtonComponent control) control.active = false;
        });
        clear.active = !ClassShortcut.UNBOUND.equals(draftBinding[0]);
        clear.visibleWhen(() -> !ClassShortcut.UNBOUND.equals(draftBinding[0]));
        binding.onPress(ignored -> {
            binding.setMessage(text("ralle.settings.keybind.press-key"));
            screen.captureOptionKey(value -> {
                draftBinding[0] = value;
                binding.setMessage(bindingLabel(value));
                clear.active = !ClassShortcut.UNBOUND.equals(value);
            });
        });
        binding.sizing(Sizing.expand(100), Sizing.fixed(20));
        binding.renderer(RalleButtonRenderers.neutral());
        bindingRow.child(clear).child(binding);
        content.child(bindingRow);
        content.child(UIComponents.label(text("ralle.classes.binding-help")).color(RalleTheme.muted()).maxWidth(266));
        var error = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFF6B6B)).maxWidth(266);
        content.child(error);
        @SuppressWarnings("rawtypes") var overlay = new OverlayContainer[1];
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
