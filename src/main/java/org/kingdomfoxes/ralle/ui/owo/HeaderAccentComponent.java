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
        int rightEdge = x + width - 1;
        int drawableHeight = Math.max(1, height - 2);
        int diagonalRun = Math.max(0, width - BOTTOM_INSET - 2);
        int denominator = Math.max(1, drawableHeight - 1);

        for (int row = 0; row < drawableHeight; row++) {
            int diagonalOffset = 1 + (row * diagonalRun + denominator - 1) / denominator;
            int whiteStart = rightEdge - diagonalOffset;
            int orangeWidth = ACCENT_THICKNESS;
            int orangeStart = Math.max(x, whiteStart - orangeWidth);

            graphics.fill(orangeStart, y + row + 1, whiteStart, y + row + 2, ORANGE);
            graphics.fill(whiteStart, y + row + 1, rightEdge, y + row + 2, WHITE);
        }
    }
}
