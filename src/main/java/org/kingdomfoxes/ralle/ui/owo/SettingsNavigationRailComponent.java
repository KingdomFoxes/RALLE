package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;

/** Stacks one navigation button and paints its non-interactive rail segment above it. */
final class SettingsNavigationRailComponent extends StackLayout {
    private static final int GOLD = 0xFFF2B84B;
    private final SettingsNavigationRailGeometry.Shape shape;
    private final int visualHeight;

    SettingsNavigationRailComponent(
            Sizing horizontalSizing,
            Sizing verticalSizing,
            SettingsNavigationRailGeometry.Shape shape,
            int visualHeight
    ) {
        super(horizontalSizing, verticalSizing);
        this.shape = shape;
        this.visualHeight = visualHeight;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        for (var segment : SettingsNavigationRailGeometry.segments(shape, width, visualHeight, height)) {
            graphics.fill(
                    x + segment.x(),
                    y + segment.y(),
                    x + segment.x() + segment.width(),
                    y + segment.y() + segment.height(),
                    GOLD
            );
        }
    }
}
