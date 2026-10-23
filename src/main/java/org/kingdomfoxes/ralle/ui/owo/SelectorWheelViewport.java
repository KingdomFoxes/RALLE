package org.kingdomfoxes.ralle.ui.owo;

import java.util.List;

/** Shared scrolling and clipping for every Bars selector, including the outward motion envelope. */
final class SelectorWheelViewport {
    static final int TOP = 6;
    static final int FOOTER = 42;
    private double scroll, maximum, originY;
    private int height;

    void layout(List<SelectorWheelLayout.Face> faces, int height) {
        this.height = height;
        double top = faces.stream().mapToInt(SelectorWheelLayout.Face::top).min().orElse(0)
                - SelectorWheelModel.EXTENSION;
        double bottom = faces.stream().mapToInt(SelectorWheelLayout.Face::bottom).max().orElse(0);
        double available = Math.max(1, bottom() - TOP - 12);
        maximum = Math.max(0, bottom - top - available);
        scroll = Math.clamp(scroll, 0, maximum);
        originY = maximum > 0 ? TOP + 6 - top - scroll
                : Math.max(TOP + 6 - top, Math.min(height / 2d, bottom() - 6 - bottom));
    }

    void reveal(SelectorWheelLayout.Face face, List<SelectorWheelLayout.Face> faces) {
        double top = originY + face.top() - SelectorWheelModel.EXTENSION;
        double bottom = originY + face.bottom();
        if (top < TOP + 6) scroll -= TOP + 6 - top;
        else if (bottom > bottom() - 6) scroll += bottom - (bottom() - 6);
        layout(faces, height);
    }

    void scroll(double amount, List<SelectorWheelLayout.Face> faces) {
        scroll += amount;
        layout(faces, height);
    }

    int bottom() { return Math.max(TOP + 1, height - FOOTER); }
    boolean contains(double y) { return y >= TOP && y < bottom(); }
    boolean scrollable() { return maximum > 0; }
    double originY() { return originY; }
}
