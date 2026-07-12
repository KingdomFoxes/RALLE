package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;

import java.util.function.BooleanSupplier;

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

    static ButtonComponent.Renderer primary() {
        return renderer(Palette.PRIMARY, () -> false);
    }

    private static ButtonComponent.Renderer renderer(Palette palette, BooleanSupplier selected) {
        return (graphics, button, delta) -> {
            int x = button.getX();
            int y = button.getY();
            int right = x + button.getWidth();
            int bottom = y + button.getHeight();

            int fill;
            int highlight;
            if (!button.active()) {
                fill = DISABLED;
                highlight = DISABLED_HIGHLIGHT;
            } else {
                var colors = selected.getAsBoolean() ? Palette.SELECTED : palette;
                fill = button.isHovered() ? colors.hovered : colors.normal;
                highlight = colors.highlight;
            }

            // Two dark pixels on the bottom and right create the offset, flat-button depth.
            graphics.fill(x, y, right, bottom, EDGE);
            graphics.fill(x, y, right - 2, bottom - 2, fill);

            // A restrained top/left highlight keeps the face legible on the dark navy panel.
            graphics.fill(x, y, right - 2, y + 1, highlight);
            graphics.fill(x, y, x + 1, bottom - 2, highlight);
        };
    }

    private enum Palette {
        NEUTRAL(0xFF263A5A, 0xFF324D77, TOP_LEFT_HIGHLIGHT),
        PRIMARY(0xFF238636, 0xFF2EA043, 0xFF53B564),
        SELECTED(0xFFB8832F, 0xFFD39B3D, 0xFFE2B45F);

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
