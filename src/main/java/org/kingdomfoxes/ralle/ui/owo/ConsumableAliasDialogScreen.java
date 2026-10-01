package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightValidation;

import java.io.IOException;

/** Compact in-screen transactional comma-separated alias modal. */
final class ConsumableAliasDialogScreen {
    private ConsumableAliasDialogScreen() {}

    static void open(RalleSettingsScreen screen, ConsumableHighlightStore store, int ruleIndex) {
        var content = UIContainers.verticalFlow(Sizing.fixed(320), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.translatable(
                "ralle.consumables.dialog.alias.title"))).color(RalleTheme.accent()));
        content.child(UIComponents.label(RalleTheme.ui(Component.translatable(
                "ralle.consumables.field.aliases"))).color(RalleTheme.muted()));
        var input = UIComponents.textBox(Sizing.fill(100));
        input.setMaxLength(512);
        content.child(input);
        var error = UIComponents.label(Component.empty()).color(Color.ofRgb(0xFF6B6B)).maxWidth(296);
        content.child(error);

        @SuppressWarnings("rawtypes")
        var overlayHolder = new OverlayContainer[1];
        var add = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.consumables.action.add")), ignored -> {
            try {
                store.addAliases(ruleIndex, ConsumableHighlightValidation.parseAliasBatch(input.getValue()));
                overlayHolder[0].remove();
            } catch (IOException | IllegalArgumentException exception) {
                error.text(RalleTheme.ui(Component.literal(exception.getMessage() == null
                        ? "Could not add aliases" : exception.getMessage())));
            }
        });
        add.sizing(Sizing.fill(100), Sizing.fixed(20));
        add.renderer(RalleButtonRenderers.primary());
        content.child(add);
        overlayHolder[0] = screen.showModal(content);
    }
}
