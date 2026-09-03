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
        var draft = new ColorStyleDraft(initial);
        var content = UIContainers.verticalFlow(Sizing.fixed(420), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.translatable(editingIndex == null
                        ? "ralle.consumables.dialog.add.title" : "ralle.consumables.dialog.color.title")))
                .color(RalleTheme.ACCENT));

        var body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(12);
        var fields = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content()).gap(5);
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

        fields.child(label("ralle.consumables.field.hex"));
        var hex = UIComponents.textBox(Sizing.fixed(104)).text(draft.hex());
        hex.setMaxLength(7);
        var hexRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20)).gap(6);
        hexRow.verticalAlignment(VerticalAlignment.CENTER);
        hexRow.child(hex);
        hexRow.child(new HighlightedSlotPreviewComponent(new WynncraftScrollPreviewItemProvider(), draft::style));
        fields.child(hexRow);
        var rainbow = UIComponents.checkbox(RalleTheme.ui(Component.translatable("ralle.consumables.field.rainbow")));
        rainbow.checked(draft.rainbow());
        rainbow.onChanged(draft::rainbow);
        fields.child(rainbow);

        var picker = new HsvWheelTrianglePicker(150, draft.rgb());
        var syncingHex = new boolean[1];
        picker.onChanged(rgb -> {
            draft.rgb(rgb);
            syncingHex[0] = true;
            hex.text(draft.hex());
            syncingHex[0] = false;
        });
        var error = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFF6B6B)).maxWidth(396);
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
                if (editingIndex == null) {
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
        content.child(body).child(error).child(submit);
        overlayHolder[0] = screen.showModal(content);
    }

    private static io.wispforest.owo.ui.component.LabelComponent label(String key) {
        return UIComponents.label(RalleTheme.ui(Component.translatable(key))).color(RalleTheme.MUTED);
    }
}
