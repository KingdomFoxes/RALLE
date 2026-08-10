package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * Flat, high-contrast button treatments shared by RALLE-owned screens.
 */
final class RalleButtonRenderers {
    private static final int EDGE = 0xFF080D16;
    private static final int TOP_LEFT_HIGHLIGHT = 0xFF66738A;
    private static final int DISABLED = 0xFF303846;
    private static final int DISABLED_HIGHLIGHT = 0xFF465064;

    private RalleButtonRenderers() {}

    static ButtonComponent.Renderer neutral() {
        return renderer(Palette.NEUTRAL, () -> false);
    }

    static ButtonComponent.Renderer selectable(BooleanSupplier selected) {
        return renderer(Palette.NEUTRAL, selected);
    }

    static ButtonComponent.Renderer warning(BooleanSupplier active) {
        return renderer(Palette.NEUTRAL, active);
    }

    static ButtonComponent.Renderer primary() {
        return renderer(Palette.PRIMARY, () -> false);
    }

    static ButtonComponent.Renderer destructive() {
        return renderer(Palette.DESTRUCTIVE, () -> false);
    }

    static ButtonComponent.Renderer destructiveX() {
        var background = destructive();
        return (graphics, button, delta) -> {
            background.draw(graphics, button, delta);
            drawPixelX(graphics, button.getX(), button.getY(),
                    button.getWidth(), button.getHeight(),
                    button.active() ? 0xFFFFFFFF : 0xFF8D96A5);
        };
    }

    static ButtonComponent.Renderer countdown(DoubleSupplier remainingFraction) {
        var background = primary();
        return (graphics, button, delta) -> {
            background.draw(graphics, button, delta);
            if (!button.active()) return;

            int faceWidth = Math.max(0, button.getWidth() - 2);
            int overlayWidth = countdownOverlayWidth(faceWidth, remainingFraction.getAsDouble());
            if (overlayWidth > 0) {
                graphics.fill(
                        button.getX(),
                        button.getY() + 1,
                        button.getX() + overlayWidth,
                        button.getY() + button.getHeight() - 2,
                        0x553CCB5A
                );
            }
        };
    }

    static int countdownOverlayWidth(int faceWidth, double remainingFraction) {
        double elapsedFraction = 1d - Math.clamp(remainingFraction, 0d, 1d);
        return (int) Math.ceil(Math.max(0, faceWidth) * elapsedFraction);
    }

    static ButtonComponent.Renderer navigation(int leftInset) {
        return (graphics, button, delta) -> {
            int x = button.getX();
            int y = button.getY();
            int right = x + button.getWidth();
            int bottom = y + button.getHeight();
            int highlightLeft = Math.clamp(x + leftInset, x, right);
            if (button.isHovered() && highlightLeft < right) {
                graphics.fill(highlightLeft, y, right, bottom, 0xA6263A5A);
            }
        };
    }

    static ButtonComponent.Renderer refresh() {
        var background = neutral();
        return (graphics, button, delta) -> {
            background.draw(graphics, button, delta);

            int left = button.getX() + (button.getWidth() - 11) / 2;
            int top = button.getY() + (button.getHeight() - 11) / 2;
            int color = button.active() ? 0xFFFFFFFF : 0xFF8D96A5;

            // Open circular arc with a downward arrowhead at the lower-left, matching the reference.
            graphics.fill(left + 3, top + 1, left + 8, top + 2, color);
            graphics.fill(left + 2, top + 2, left + 3, top + 3, color);
            graphics.fill(left + 8, top + 2, left + 9, top + 3, color);
            graphics.fill(left + 1, top + 3, left + 2, top + 6, color);
            graphics.fill(left + 9, top + 3, left + 10, top + 8, color);
            graphics.fill(left + 8, top + 8, left + 9, top + 9, color);
            graphics.fill(left + 6, top + 9, left + 8, top + 10, color);

            // Solid left-pointing arrowhead, offset one pixel left from the arc.
            graphics.fill(left - 1, top + 5, left + 5, top + 6, color);
            graphics.fill(left, top + 6, left + 5, top + 7, color);
            graphics.fill(left + 1, top + 7, left + 4, top + 8, color);
            graphics.fill(left + 2, top + 8, left + 3, top + 9, color);
        };
    }

    private static ButtonComponent.Renderer renderer(Palette palette, BooleanSupplier selected) {
        return (graphics, button, delta) -> {
            drawFace(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                    selected.getAsBoolean() ? Palette.SELECTED : palette,
                    button.isHovered(), button.active());
        };
    }

    static void drawRaw(GuiGraphics graphics, int x, int y, int width, int height,
                        Kind kind, boolean hovered, boolean active) {
        var palette = switch (kind) {
            case NEUTRAL -> Palette.NEUTRAL;
            case PRIMARY -> Palette.PRIMARY;
            case DESTRUCTIVE -> Palette.DESTRUCTIVE;
        };
        drawFace(graphics, x, y, width, height, palette, hovered, active);
    }

    static void drawPixelX(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int left = x + (width - 10) / 2 - 1;
        int top = y + (height - 10) / 2 - 1;
        for (int step = 0; step < 9; step++) {
            graphics.fill(left + step, top + step, left + step + 2, top + step + 2, color);
            graphics.fill(left + 8 - step, top + step, left + 10 - step, top + step + 2, color);
        }
    }

    private static void drawFace(GuiGraphics graphics, int x, int y, int width, int height,
                                 Palette palette, boolean hovered, boolean active) {
        int right = x + width;
        int bottom = y + height;
        int fill = active ? (hovered ? palette.hovered : palette.normal) : DISABLED;
        int highlight = active ? palette.highlight : DISABLED_HIGHLIGHT;

        // Two dark pixels on the bottom and right create the offset, flat-button depth.
        graphics.fill(x, y, right, bottom, EDGE);
        graphics.fill(x, y, right - 2, bottom - 2, fill);

        // A restrained top/left highlight keeps the face legible on the dark navy panel.
        graphics.fill(x, y, right - 2, y + 1, highlight);
        graphics.fill(x, y, x + 1, bottom - 2, highlight);
    }

    enum Kind {
        NEUTRAL,
        PRIMARY,
        DESTRUCTIVE
    }

    private enum Palette {
        NEUTRAL(0xFF263A5A, 0xFF324D77, TOP_LEFT_HIGHLIGHT),
        PRIMARY(0xFF238636, 0xFF2EA043, 0xFF53B564),
        SELECTED(0xFFB8832F, 0xFFD39B3D, 0xFFE2B45F),
        DESTRUCTIVE(0xFF9F2D36, 0xFFC13B46, 0xFFE26973);

        private final int normal;
        private final int hovered;
        private final int highlight;

        Palette(int normal, int hovered, int highlight) {
            this.normal = normal;
            this.hovered = hovered;
            this.highlight = highlight;
        }
    }
}
