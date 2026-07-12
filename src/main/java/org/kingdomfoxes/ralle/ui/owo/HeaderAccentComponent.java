package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;

/** Draws the responsive diagonal accent used by Kingdom of Foxes headers. */
final class HeaderAccentComponent extends BaseUIComponent {
    private static final int ORANGE = 0xFFF18715;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ACCENT_THICKNESS = 3;
    private static final int BOTTOM_INSET = 10;

    HeaderAccentComponent(int width, int height) {
        this.sizing(Sizing.fixed(width), Sizing.fixed(height));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int diagonalRun = Math.max(0, width - BOTTOM_INSET - 1);
        int denominator = Math.max(1, height - 1);

        for (int row = 0; row < height; row++) {
            int whiteStart = x + width - (row * diagonalRun / denominator);
            int orangeWidth = Math.min(ACCENT_THICKNESS, row + 1);
            int orangeStart = Math.max(x, whiteStart - orangeWidth);

            graphics.fill(orangeStart, y + row, whiteStart, y + row + 1, ORANGE);
            graphics.fill(whiteStart, y + row, x + width, y + row + 1, WHITE);
        }
    }
}
