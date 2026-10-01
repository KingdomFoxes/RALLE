package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.OwoUIGraphics;

/** Shared surfaces for RALLE-owned screens and compact control widgets. */
final class RalleSurfaces {
    static final Surface SUPPORTER_LOCK = (context, component) -> Surface.flat(
            0x66000000 | org.kingdomfoxes.ralle.ui.theme.RallePalette.background()).draw(context, component);
    static final Surface FRAMED_NAVY = (context, component) -> framedNavy(
            context, component.x(), component.y(), component.width(), component.height());
    static final Surface NAVY_PANEL = (context, component) -> {
        Surface.flat(org.kingdomfoxes.ralle.ui.theme.RallePalette.panelArgb())
                .and(Surface.outline(0xFF000000 | org.kingdomfoxes.ralle.ui.theme.RallePalette.insetAccentArgb()))
                .draw(context, component);
    };
    static final Surface NAVY_ROW = (context, component) -> {
        Surface.flat(org.kingdomfoxes.ralle.ui.theme.RallePalette.rowArgb())
                .and(Surface.outline(org.kingdomfoxes.ralle.ui.theme.RallePalette.rowOutlineArgb()))
                .draw(context, component);
    };

    private RalleSurfaces() {}

    static void framedNavy(OwoUIGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height,
                0xFF000000 | org.kingdomfoxes.ralle.ui.theme.RallePalette.background());
        graphics.drawRectOutline(x, y, width, height,
                org.kingdomfoxes.ralle.ui.theme.RallePalette.frameArgb());
    }
}
