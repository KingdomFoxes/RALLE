package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.Surface;

/** Shared surfaces for RALLE-owned screens and compact control widgets. */
final class RalleSurfaces {
    static final Surface FRAMED_NAVY = Surface.flat(0xFF041330).and(Surface.outline(0xFFFFFFFF));
    static final Surface NAVY_PANEL = Surface.flat(0xF20A1830).and(Surface.outline(0xFF35445F));
    static final Surface NAVY_ROW = Surface.flat(0xD91A1E27).and(Surface.outline(0xFF3B4354));

    private RalleSurfaces() {}
}
