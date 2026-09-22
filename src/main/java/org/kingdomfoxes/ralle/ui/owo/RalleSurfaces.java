package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.Surface;

/** Shared surfaces for RALLE-owned screens and compact control widgets. */
final class RalleSurfaces {
    static final Surface FRAMED_NAVY = (context, component) -> {
        Surface.flat(0xFF000000 | org.kingdomfoxes.ralle.ui.theme.RallePalette.background())
                .and(Surface.outline(org.kingdomfoxes.ralle.ui.theme.RallePalette.frameArgb()))
                .draw(context, component);
    };
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
}
