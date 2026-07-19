package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;

final class RalleScrollContainer extends ScrollContainer<FlowLayout> {
    private int wheelStep;

    RalleScrollContainer(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    RalleScrollContainer wheelStep(int wheelStep) {
        if (wheelStep < 0) throw new IllegalArgumentException("Wheel step must not be negative");
        this.wheelStep = wheelStep;
        return this;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (wheelStep == 0) return super.onMouseScroll(mouseX, mouseY, amount);
        if (child.onMouseScroll(x + mouseX - child.x(), y + mouseY - child.y(), amount)) return true;
        scrollBy(-amount * wheelStep, true, true);
        return true;
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

    void scrollToImmediately(double progress) {
        scrollTo(progress);
        currentScrollPosition = scrollOffset;
    }

    void scrollToOffsetImmediately(double offset) {
        scrollOffset = Math.clamp(offset, 0, maxScroll);
        currentScrollPosition = scrollOffset;
    }
}
