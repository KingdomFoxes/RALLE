package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightRule;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightValidation;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import java.io.IOException;

/** Shared in-screen create/edit color modal with synchronized HSV, hex, rainbow, and preview state. */
final class ConsumableColorDialogScreen {
    private static final int ADD_DIALOG_WIDTH = 420;
    private static final int EDIT_DIALOG_WIDTH = 290;
    private static final int EDIT_FIELDS_WIDTH = 134;
    private static final int EDIT_PICKER_SIZE = 128;
    private static final int ADD_PICKER_SIZE = 150;
    private static final int DIALOG_PADDING = 10;

    private ConsumableColorDialogScreen() {}

    static void openAdd(RalleSettingsScreen screen, ConsumableHighlightStore store) {
        open(screen, store, null, new HighlightStyle(0xF2B84B, false));
    }

    static void openEdit(RalleSettingsScreen screen, ConsumableHighlightStore store, int ruleIndex) {
        open(screen, store, ruleIndex, store.snapshot().get(ruleIndex).style());
    }

    private static void open(
            RalleSettingsScreen screen,
            ConsumableHighlightStore store,
            Integer editingIndex,
            HighlightStyle initial
    ) {
        open(screen, store, editingIndex, initial, null);
    }

    static void openQueueColor(RalleSettingsScreen screen, org.kingdomfoxes.ralle.api.settings.ColorSetting setting) {
        open(screen, null, 0, setting.value(), setting);
    }

    private static void open(RalleSettingsScreen screen, ConsumableHighlightStore store, Integer editingIndex,
                             HighlightStyle initial, org.kingdomfoxes.ralle.api.settings.ColorSetting queueColor) {
        var draft = new ColorStyleDraft(initial);
        boolean editing = editingIndex != null;
        int dialogWidth = editing ? EDIT_DIALOG_WIDTH : ADD_DIALOG_WIDTH;
        int innerWidth = dialogWidth - DIALOG_PADDING * 2;
        var content = UIContainers.verticalFlow(Sizing.fixed(dialogWidth), Sizing.content());
        content.gap(6).padding(Insets.of(DIALOG_PADDING)).surface(RalleSurfaces.FRAMED_NAVY);

        var body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(8);
        // Bottom-align short controls with the wheel, directly above the full-width Apply button.
        if (editing) body.verticalAlignment(VerticalAlignment.BOTTOM);
        var fields = UIContainers.verticalFlow(
                editing ? Sizing.fixed(EDIT_FIELDS_WIDTH) : Sizing.expand(100), Sizing.content()).gap(5);
        var title = UIComponents.label(RalleTheme.ui(Component.translatable(queueColor != null ? "ralle.war.queue.color.title" : editingIndex == null
                        ? "ralle.consumables.dialog.add.title" : "ralle.consumables.dialog.color.title")))
                .color(RalleTheme.accent());
        if (editing) content.child(title);
        else fields.child(title);
        if (queueColor != null) content.child(new QueueColorPreviewComponent(draft::style).margins(Insets.top(4)));
        TextBoxComponent name = editingIndex == null ? UIComponents.textBox(Sizing.fill(100)) : null;
        TextBoxComponent aliases = editingIndex == null ? UIComponents.textBox(Sizing.fill(100)) : null;
        if (name != null) {
            name.setMaxLength(50);
            fields.child(label("ralle.consumables.field.word"));
            fields.child(name);
            aliases.setMaxLength(512);
            fields.child(label("ralle.consumables.field.aliases"));
            fields.child(aliases);
        }

        fields.child(label("ralle.consumables.field.hex").margins(Insets.left(1)));
        var hex = UIComponents.textBox(Sizing.fixed(104)).text(draft.hex());
        hex.setMaxLength(7);
        var hexRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20)).gap(6);
        hexRow.verticalAlignment(VerticalAlignment.CENTER);
        hexRow.child(hex);
        if (queueColor == null) hexRow.child(new HighlightedSlotPreviewComponent(new WynncraftScrollPreviewItemProvider(), draft::style));
        fields.child(hexRow);
        var rainbow = UIComponents.checkbox(RalleTheme.ui(Component.translatable("ralle.consumables.field.rainbow")));
        var chroma = UIComponents.checkbox(RalleTheme.ui(Component.translatable("ralle.consumables.field.chroma")));
        rainbow.checked(draft.rainbow());
        chroma.checked(draft.chroma());
        rainbow.onChanged(value -> { draft.rainbow(value); if (value) chroma.checked(false); });
        chroma.onChanged(value -> { draft.chroma(value); if (value) rainbow.checked(false); });
        fields.child(rainbow);
        fields.child(chroma);

        var picker = new HsvWheelTrianglePicker(editing ? EDIT_PICKER_SIZE : ADD_PICKER_SIZE, draft.rgb());
        var syncingHex = new boolean[1];
        picker.onChanged(rgb -> {
            draft.rgb(rgb);
            syncingHex[0] = true;
            hex.text(draft.hex());
            syncingHex[0] = false;
        });
        var error = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFF6B6B)).maxWidth(innerWidth);
        hex.onChanged().subscribe(value -> {
            if (syncingHex[0] || !value.matches("#[0-9A-Fa-f]{6}")) return;
            draft.hex(value);
            picker.rgb(draft.rgb());
            error.text(Component.empty());
        });
        @SuppressWarnings("rawtypes")
        var overlayHolder = new OverlayContainer[1];
        var submit = UIComponents.button(RalleTheme.ui(Component.translatable(editingIndex == null
                ? "ralle.consumables.action.add" : "ralle.consumables.action.apply")), ignored -> {
            try {
                if (!hex.getValue().matches("#[0-9A-Fa-f]{6}")) {
                    throw new IllegalArgumentException("Color must use #RRGGBB");
                }
                draft.hex(hex.getValue());
                if (queueColor != null) {
                    queueColor.set(draft.style());
                } else if (editingIndex == null) {
                    store.add(new ConsumableHighlightRule(
                            name.getValue(),
                            ConsumableHighlightValidation.parseAliasBatch(aliases.getValue()),
                            draft.style()));
                } else {
                    store.updateStyle(editingIndex, draft.style());
                }
                overlayHolder[0].remove();
            } catch (IOException | IllegalArgumentException exception) {
                error.text(RalleTheme.ui(Component.literal(exception.getMessage() == null
                        ? "Could not save this highlight rule" : exception.getMessage())));
            }
        });
        submit.sizing(Sizing.fill(100), Sizing.fixed(20));
        submit.renderer(RalleButtonRenderers.primary());
        body.child(fields).child(picker);
        content.child(body).child(error);
        content.child(submit);
        overlayHolder[0] = screen.showModal(content);
    }

    private static io.wispforest.owo.ui.component.LabelComponent label(String key) {
        return UIComponents.label(RalleTheme.ui(Component.translatable(key))).color(RalleTheme.muted());
    }
}
