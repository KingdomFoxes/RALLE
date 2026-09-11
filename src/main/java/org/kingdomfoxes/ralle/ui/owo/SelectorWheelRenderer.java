package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pixel-aligned annular faces shared by both modifier wheels. Runs coalesce adjacent pixels. */
final class SelectorWheelRenderer {
    private static final Map<Face, List<Run>> FACES = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Face, List<Run>> eldest) { return size() > 32; }
    };
    private SelectorWheelRenderer() {}

    static void draw(GuiGraphics graphics, SelectorWheelModel.Sector sector, int dx, int dy, boolean selected) {
        for (var run : FACES.computeIfAbsent(new Face(sector, selected), SelectorWheelRenderer::rasterize)) {
            graphics.fill(run.left() + dx, run.y() + dy, run.right() + dx, run.y() + dy + 1, run.color());
        }
    }

    private static List<Run> rasterize(Face face) {
        var sector = face.sector();
        var runs = new ArrayList<Run>();
        int extent = (int) Math.ceil(sector.outer());
        for (int y = -extent; y < extent; y++) {
            int start = -extent;
            int previous = 0;
            for (int x = -extent; x <= extent; x++) {
                int color = x == extent ? 0 : color(sector, x + .5, y + .5, face.selected());
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
        if (!sector.contains(x, y, 1)) return selected ? 0xFF000000 | RalleTheme.ACCENT_RGB : 0xFF586985;
        if (!sector.contains(x + 1, y + 2, 0)) return 0xFF030A18;
        if (!sector.contains(x - 1, y - 1, 0)) return 0xFF71819B;
        return selected ? 0xF0223552 : 0xEE0A1830;
    }
    private record Face(SelectorWheelModel.Sector sector, boolean selected) {}
    private record Run(int left, int right, int y, int color) {}
}
