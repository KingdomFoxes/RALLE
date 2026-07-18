package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

final class RalleScrollContainer extends ScrollContainer<FlowLayout> {
    RalleScrollContainer(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    double progress() {
        return maxScroll <= 0 ? 0 : scrollOffset / maxScroll;
    }

    double offset() {
        return scrollOffset;
    }

    int maximumOffset() {
        return maxScroll;
    }

    boolean atMaximum() {
        return maxScroll > 0 && scrollOffset >= maxScroll - .5;
    }

    void scrollToImmediately(double progress) {
        scrollTo(progress);
        currentScrollPosition = scrollOffset;
    }

    void scrollToImmediately(UIComponent component) {
        scrollTo(component);
        currentScrollPosition = scrollOffset;
    }
}
