package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

/** Shared consumable/class editor proportions: scrolling list and a fixed right-hand action lane. */
final class SettingsListEditorColumns extends FlowLayout {
    static final int ACTION_WIDTH = 104;
    static final int CONTENT_HEIGHT = 220;
    private final FlowLayout actions;
    private final RalleScrollContainer scroll;

    SettingsListEditorColumns(UIComponent list) {
        super(Sizing.fill(100), Sizing.fixed(230), Algorithm.HORIZONTAL);
        gap(6);
        var content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(2).padding(Insets.of(4));
        content.child(list);
        scroll = new RalleScrollContainer(Sizing.expand(100), Sizing.fixed(CONTENT_HEIGHT), content);
        scroll.wheelStep(18).scrollbarThiccness(3).surface(RalleSurfaces.NAVY_PANEL);
        child(scroll);
        actions = UIContainers.verticalFlow(Sizing.fixed(ACTION_WIDTH), Sizing.fixed(CONTENT_HEIGHT));
        actions.gap(5).padding(Insets.of(4)).surface(RalleSurfaces.NAVY_PANEL);
        child(actions);
    }

    FlowLayout actions() { return actions; }
    RalleScrollContainer scroll() { return scroll; }
}
