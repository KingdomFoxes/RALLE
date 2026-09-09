package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;

/** RALLE modal backdrop: dispatches to its content, then always consumes the press. */
final class RalleModalOverlay<C extends UIComponent> extends OverlayContainer<C> {
    RalleModalOverlay(C child) {
        super(child);
        closeOnClick(false);
    }

    @Override
    public void mount(ParentUIComponent parent, int x, int y) {
        super.mount(parent, x, y);
        // Screens own topmost-only Escape dispatch; suppress OverlayContainer's parallel listener.
        if (exitSubscription != null) {
            exitSubscription.cancel();
            exitSubscription = null;
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent event, boolean doubled) {
        super.onMouseDown(event, doubled);
        return true;
    }
}
