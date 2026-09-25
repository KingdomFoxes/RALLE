package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.ui.theme.RallePalette;
import org.kingdomfoxes.ralle.ui.theme.RalleThemeCatalog;
import org.kingdomfoxes.ralle.ui.theme.ThemeJsonExport;
import org.kingdomfoxes.ralle.ui.theme.ThemePreviewSession;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compact in-place theme preview editor. It owns no persisted state. */
final class ThemeEditorPanel {
    static final int WIDTH = 244;
    private static final int INPUT_WIDTH = 104;
    private static final int PICKER_SIZE = 112;
    private final FlowLayout panel;
    private final Map<ThemePreviewSession.Role, TextBoxComponent> fields = new EnumMap<>(ThemePreviewSession.Role.class);
    private final Map<ThemePreviewSession.Role, ColorStyleDraft> drafts = new EnumMap<>(ThemePreviewSession.Role.class);
    private final List<LabelBinding> labels = new ArrayList<>();
    private final ThemePreviewSession session = RallePalette.previewSession();
    private final LabelRef selectedMeta = new LabelRef();
    private final LabelRef activeLabel = new LabelRef();
    private final LabelRef errorLabel = new LabelRef();
    private final ButtonComponent copyButton;
    private final HsvWheelTrianglePicker picker;
    private ThemePreviewSession.Role activeRole = ThemePreviewSession.Role.BACKGROUND;
    private String loadedThemeId;
    private boolean syncing;
    private boolean allValid = true;
    private long copiedUntil;

    ThemeEditorPanel() {
        var theme = RallePalette.theme();
        loadedThemeId = theme.id();
        var content = UIContainers.verticalFlow(Sizing.fixed(WIDTH), Sizing.content());
        content.gap(5).padding(Insets.of(10)).surface(RalleSurfaces.FRAMED_NAVY);
        var title = UIComponents.label(RalleTheme.ui(Component.translatable("ralle.theme-editor.title"))).color(RalleTheme.accent());
        labels.add(new LabelBinding(title, Component.translatable("ralle.theme-editor.title"), true));
        content.child(title);
        selectedMeta.label = UIComponents.label(RalleTheme.ui(Component.literal(""))).color(RalleTheme.muted()).maxWidth(WIDTH - 20);
        content.child(selectedMeta.label);

        var controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(8);
        var column = UIContainers.verticalFlow(Sizing.fixed(INPUT_WIDTH), Sizing.content()).gap(4);
        for (var role : ThemePreviewSession.Role.values()) {
            var group = UIContainers.verticalFlow(Sizing.fixed(INPUT_WIDTH), Sizing.content()).gap(1);
            var label = UIComponents.label(RalleTheme.ui(Component.translatable("ralle.theme-editor." + role.name().toLowerCase()))).color(RalleTheme.muted());
            labels.add(new LabelBinding(label, Component.translatable("ralle.theme-editor." + role.name().toLowerCase()), false));
            var input = UIComponents.textBox(Sizing.fixed(INPUT_WIDTH)).text(ThemeJsonExport.hex(color(theme, role)));
            input.verticalSizing(Sizing.fixed(20));
            input.setMaxLength(7);
            fields.put(role, input);
            drafts.put(role, new ColorStyleDraft(new HighlightStyle(color(theme, role), false)));
            input.onChanged().subscribe(value -> inputChanged(role, value));
            group.child(label).child(input);
            column.child(group);
        }
        controls.child(column);
        picker = new HsvWheelTrianglePicker(PICKER_SIZE, color(theme, activeRole));
        picker.onChanged(rgb -> pickerChanged(rgb));
        controls.child(picker);
        content.child(controls);

        activeLabel.label = UIComponents.label(editingLabel(ThemePreviewSession.Role.BACKGROUND)).color(RalleTheme.accent());
        content.child(activeLabel.label);
        var actions = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24)).gap(4);
        errorLabel.label = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFFFF6666)).maxWidth(WIDTH - 20);
        copyButton = new ThemeCopyButton(ignored -> copy());
        copyButton.sizing(Sizing.fixed(24), Sizing.fixed(24));
        copyButton.renderer(RalleButtonRenderers.neutral());
        copyButton.tooltip(RalleTheme.ui(Component.translatable("ralle.theme-editor.copy-tooltip")));
        actions.child(copyButton);
        content.child(actions).child(errorLabel.label);
        panel = content;
        refreshMetadata(theme);
    }

    FlowLayout component() { return panel; }

    void tick() {
        boolean karla = RalleTypography.usesKarla();
        if (fontKarla != karla) {
            fontKarla = karla;
            refreshLabelStyles();
        }
        for (var role : ThemePreviewSession.Role.values()) {
            if (fields.get(role).isFocused() && activeRole != role) {
                activeRole = role;
                picker.rgb(drafts.get(role).rgb());
                updateActiveLabel();
                break;
            }
        }
        if (copiedUntil != 0 && System.currentTimeMillis() >= copiedUntil) {
            copiedUntil = 0;
            errorLabel.label.text(Component.empty());
        }
    }
    private boolean fontKarla = RalleTypography.usesKarla();

    void refreshPalette() {
        for (var binding : labels) binding.label.color(binding.accent ? RalleTheme.accent() : RalleTheme.muted());
        selectedMeta.label.color(RalleTheme.muted());
        activeLabel.label.color(RalleTheme.accent());
        refreshLabelStyles();
    }

    private void refreshLabelStyles() {
        for (var binding : labels) binding.label.text(RalleTheme.ui(binding.source));
        refreshMetadata(RallePalette.theme());
        updateActiveLabel();
    }

    boolean selectInputAt(double mouseX, double mouseY) {
        for (var role : ThemePreviewSession.Role.values()) {
            var field = fields.get(role);
            if (mouseX >= field.x() && mouseX < field.x() + field.width()
                    && mouseY >= field.y() && mouseY < field.y() + field.height()) {
                activeRole = role;
                picker.rgb(drafts.get(role).rgb());
                updateActiveLabel();
                return true;
            }
        }
        return false;
    }

    void syncSelectedTheme() {
        var theme = RallePalette.theme();
        if (!theme.id().equals(loadedThemeId)) {
            loadedThemeId = theme.id();
            activeRole = ThemePreviewSession.Role.BACKGROUND;
            syncing = true;
            for (var role : ThemePreviewSession.Role.values()) {
                int rgb = color(theme, role);
                drafts.put(role, new ColorStyleDraft(new HighlightStyle(rgb, false)));
                fields.get(role).text(ThemeJsonExport.hex(rgb));
            }
            picker.rgb(color(theme, activeRole));
            syncing = false;
            errorLabel.label.text(Component.empty());
            allValid = true;
            validateAll();
            refreshMetadata(theme);
            updateActiveLabel();
        }
    }

    private void inputChanged(ThemePreviewSession.Role role, String value) {
        if (syncing) return;
        activeRole = role;
        updateActiveLabel();
        if (!ThemeJsonExport.validHex(value)) {
            errorLabel.label.text(RalleTheme.ui(Component.translatable("ralle.theme-editor.invalid")));
            validateAll();
            return;
        }
        int rgb = ThemeJsonExport.parseHex(value);
        drafts.get(role).rgb(rgb);
        session.setColor(loadedThemeId, role, rgb);
        picker.rgb(rgb);
        errorLabel.label.text(Component.empty());
        validateAll();
    }

    private void pickerChanged(int rgb) {
        var field = fields.get(activeRole);
        String value = ThemeJsonExport.hex(rgb);
        syncing = true;
        field.text(value);
        syncing = false;
        drafts.get(activeRole).rgb(rgb);
        session.setColor(loadedThemeId, activeRole, rgb);
        errorLabel.label.text(Component.empty());
        validateAll();
    }

    private void validateAll() {
        allValid = fields.values().stream().allMatch(field -> ThemeJsonExport.validHex(field.getValue()));
        copyButton.active = allValid;
    }

    private void copy() {
        if (!allValid) return;
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(ThemeJsonExport.serialize(session.effectiveTheme(loadedThemeId)));
            errorLabel.label.color(Color.ofRgb(0xFF67D391));
            errorLabel.label.text(RalleTheme.ui(Component.translatable("ralle.theme-editor.copied")));
            copiedUntil = System.currentTimeMillis() + 1800;
        } catch (RuntimeException exception) {
            errorLabel.label.color(Color.ofRgb(0xFFFF6666));
            errorLabel.label.text(RalleTheme.ui(Component.translatable("ralle.theme-editor.copy-failed")));
        }
    }

    private void refreshMetadata(RalleThemeCatalog.Theme theme) {
        String creator = theme.id().equals(RalleThemeCatalog.DEFAULT_ID) ? "RALLE" : theme.contributor();
        selectedMeta.label.text(RalleTheme.ui(Component.literal(theme.name()
                + (creator == null ? "" : "\n" + creator))));
    }
    private void updateActiveLabel() { activeLabel.label.text(editingLabel(activeRole)); }
    private static Component editingLabel(ThemePreviewSession.Role role) {
        return RalleTheme.ui(Component.translatable("ralle.theme-editor.editing", Component.translatable(
                "ralle.theme-editor." + role.name().toLowerCase())));
    }
    private static String title(ThemePreviewSession.Role role) {
        return switch (role) { case BACKGROUND -> "Background"; case OUTLINE -> "Outline"; case ACCENT -> "Accent"; };
    }
    private static int color(RalleThemeCatalog.Theme theme, ThemePreviewSession.Role role) {
        return switch (role) { case BACKGROUND -> theme.background(); case OUTLINE -> theme.outline(); case ACCENT -> theme.accent(); };
    }

    private static final class LabelRef { io.wispforest.owo.ui.component.LabelComponent label; }
    private record LabelBinding(io.wispforest.owo.ui.component.LabelComponent label, Component source, boolean accent) {}

    private static final class ThemeCopyButton extends ButtonComponent {
        private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("ralle", "textures/gui/theme/toolytom_head.png");
        ThemeCopyButton(java.util.function.Consumer<ButtonComponent> onPress) {
            super(RalleTheme.ui(Component.translatable("ralle.theme-editor.copy-tooltip")), onPress);
        }
        @Override public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            renderer().draw((OwoUIGraphics) graphics, this, delta);
            int iconX = getX() + (getWidth() - 16) / 2;
            int iconY = getY() + (getHeight() - 16) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, iconX, iconY, 0, 0, 16, 16, 16, 16);
        }
    }
}
