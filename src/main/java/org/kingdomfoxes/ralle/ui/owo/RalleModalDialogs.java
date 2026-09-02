package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

/** Shared compact confirmation overlay matching the Raid LFG modal language. */
final class RalleModalDialogs {
    private RalleModalDialogs() {}

    static void confirm(
            RalleSettingsScreen screen,
            Component title,
            Component message,
            Component confirmLabel,
            boolean destructive,
            Runnable accepted
    ) {
        var content = UIContainers.verticalFlow(Sizing.fixed(320), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(title)).color(RalleTheme.ACCENT));
        content.child(UIComponents.label(RalleTheme.ui(message)).color(RalleTheme.MUTED).maxWidth(296));

        var controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(8);
        @SuppressWarnings("rawtypes")
        var overlayHolder = new OverlayContainer[1];
        var confirm = UIComponents.button(RalleTheme.ui(confirmLabel), ignored -> {
            overlayHolder[0].remove();
            accepted.run();
        });
        confirm.horizontalSizing(Sizing.fill(50));
        confirm.renderer(destructive ? RalleButtonRenderers.destructive() : RalleButtonRenderers.primary());
        var cancel = UIComponents.button(RalleTheme.ui(Component.translatable("gui.cancel")),
                ignored -> overlayHolder[0].remove());
        cancel.horizontalSizing(Sizing.fill(50));
        cancel.renderer(RalleButtonRenderers.neutral());
        controls.child(confirm).child(cancel);
        content.child(controls);
        overlayHolder[0] = screen.showModal(content);
    }
}
