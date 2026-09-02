package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;

/** Horizontal row exposing hover state only to its reserved icon-action lane. */
final class HoverActionRow extends FlowLayout {
    HoverActionRow(Sizing horizontalSizing, Sizing verticalSizing) {
        super(horizontalSizing, verticalSizing, Algorithm.HORIZONTAL);
    }

    boolean actionsVisible() {
        return hovered;
    }
}
