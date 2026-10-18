package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared pixel-aligned faces. Annular runs coalesce adjacent pixels; bars use bounded rectangles. */
final class SelectorWheelRenderer {
    private static final Map<Face, List<Run>> FACES = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Face, List<Run>> eldest) { return size() > 32; }
    };
    private SelectorWheelRenderer() {}

    static void drawFace(GuiGraphics graphics, SelectorWheelLayout.Face face, int dx, int dy, boolean selected) {
        if (face instanceof SelectorWheelLayout.Circle circle) {
            draw(graphics, circle.sector(), dx, dy, selected);
            return;
        }
        int left = face.left() + dx, top = face.top() + dy;
        int right = face.right() + dx, bottom = face.bottom() + dy;
        graphics.fill(left, top, right, bottom, outlineColor(selected));
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fillColor(selected));
    }

    static void draw(GuiGraphics graphics, SelectorWheelModel.Sector sector, int dx, int dy, boolean selected) {
        for (var run : FACES.computeIfAbsent(new Face(sector, selected,
                org.kingdomfoxes.ralle.ui.theme.RallePalette.revision()), SelectorWheelRenderer::rasterize)) {
            graphics.fill(run.left() + dx, run.y() + dy, run.right() + dx, run.y() + dy + 1, run.color());
        }
    }

    static void drawCreate(GuiGraphics graphics, SelectorWheelModel.Sector sector,
                           int dx, int dy, boolean selected) {
        draw(graphics, sector, dx, dy, selected);
    }

    private static List<Run> rasterize(Face face) {
        var sector = face.sector();
        var runs = new ArrayList<Run>();
        int extent = (int) Math.ceil(sector.outer());
        for (int y = -extent; y < extent; y++) {
            int start = -extent;
            int previous = 0;
            for (int x = -extent; x <= extent; x++) {
                int color = x == extent ? 0 : color(sector, x + .5, y + .5,
                        face.selected());
                if (color == previous) continue;
                if (previous != 0) runs.add(new Run(start, x, y, previous));
                start = x;
                previous = color;
            }
        }
        return List.copyOf(runs);
    }

    static int color(SelectorWheelModel.Sector sector, double x, double y, boolean selected) {
        if (!sector.contains(x, y, 0)) return 0;
        if (!sector.contains(x, y, 1)) return outlineColor(selected);
        return fillColor(selected);
    }

    static int outlineColor(boolean selected) {
        return selected
                ? org.kingdomfoxes.ralle.ui.theme.RallePalette.accentArgb()
                : org.kingdomfoxes.ralle.ui.theme.RallePalette.frameArgb();
    }

    static int fillColor(boolean selected) {
        int background = org.kingdomfoxes.ralle.ui.theme.RallePalette.background();
        int inner = background == 0x041330 ? (selected ? 0x223552 : 0x0A1830)
                : org.kingdomfoxes.ralle.ui.theme.RallePalette.secondarySurface();
        return (selected ? 0xF0 : 0xEE) << 24 | inner;
    }

    static int createColor(SelectorWheelModel.Sector sector, double x, double y, boolean selected) {
        return color(sector, x, y, selected);
    }
    private record Face(SelectorWheelModel.Sector sector, boolean selected, long revision) {}
    private record Run(int left, int right, int y, int color) {}
}
