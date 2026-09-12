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
        draw(graphics, sector, dx, dy, selected, false);
    }

    static void drawCreate(GuiGraphics graphics, SelectorWheelModel.Sector sector,
                           int dx, int dy, boolean selected) {
        draw(graphics, sector, dx, dy, selected, true);
    }

    private static void draw(GuiGraphics graphics, SelectorWheelModel.Sector sector,
                             int dx, int dy, boolean selected, boolean cleanFace) {
        for (var run : FACES.computeIfAbsent(new Face(sector, selected, cleanFace), SelectorWheelRenderer::rasterize)) {
            graphics.fill(run.left() + dx, run.y() + dy, run.right() + dx, run.y() + dy + 1, run.color());
        }
    }

    static void drawDissolving(GuiGraphics graphics, SelectorWheelModel.Sector sector,
                               int dx, int dy, double progress) {
        dissolvingPixels(sector, dx, dy, progress,
                (x, y, color) -> graphics.fill(x, y, x + 1, y + 1, color));
    }

    @FunctionalInterface interface Pixel { void draw(int x, int y, int color); }

    static void dissolvingPixels(SelectorWheelModel.Sector sector, int dx, int dy,
                                 double progress, Pixel pixel) {
        if (progress >= 1) return;
        for (var run : FACES.computeIfAbsent(new Face(sector, true, true), SelectorWheelRenderer::rasterize)) {
            for (int x = run.left(); x < run.right();) {
                int clusterSize = Math.min(CreateWheelAnimation.clusterSize(x, run.y()), run.right() - x);
                double opacity = CreateWheelAnimation.opacity(x, run.y(), progress);
                if (opacity <= 0) { x += clusterSize; continue; }
                double dust = 1 - opacity;
                int px = x + dx + (int) Math.round(dust * (3 + 4 * CreateWheelAnimation.threshold(x, run.y())));
                int py = run.y() + dy - (int) Math.round(dust * 6);
                int alpha = (int) Math.round((run.color() >>> 24) * opacity);
                int color = alpha << 24 | run.color() & 0xFFFFFF;
                for (int within = 0; within < clusterSize; within++) pixel.draw(px + within, py, color);
                x += clusterSize;
            }
        }
    }

    /** Apply the same pixel mask to native items and either font, preserving their actual rendering. */
    static void clipDissolving(GuiGraphics graphics, int left, int top, int right, int bottom,
                               int dx, int dy, double progress, Runnable draw) {
        if (progress >= 1) return;
        if (progress <= .08) { draw.run(); return; }
        for (int y = top; y < bottom; y++) {
            int start = left;
            boolean inRun = false;
            for (int x = left; x < right;) {
                int anchorX = x - dx;
                int clusterSize = Math.min(CreateWheelAnimation.clusterSize(anchorX, y - dy), right - x);
                boolean visible = CreateWheelAnimation.visible(anchorX, y - dy, progress);
                if (visible && !inRun) { start = x; inRun = true; }
                if (!visible && inRun) {
                    graphics.enableScissor(start, y, x, y + 1);
                    try { draw.run(); } finally { graphics.disableScissor(); }
                    inRun = false;
                }
                x += clusterSize;
                if (x == right && inRun) {
                    graphics.enableScissor(start, y, right, y + 1);
                    try { draw.run(); } finally { graphics.disableScissor(); }
                }
            }
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
                int color = x == extent ? 0 : color(sector, x + .5, y + .5,
                        face.selected(), face.cleanFace());
                if (color == previous) continue;
                if (previous != 0) runs.add(new Run(start, x, y, previous));
                start = x;
                previous = color;
            }
        }
        return List.copyOf(runs);
    }

    static int color(SelectorWheelModel.Sector sector, double x, double y, boolean selected) {
        return color(sector, x, y, selected, false);
    }

    static int createColor(SelectorWheelModel.Sector sector, double x, double y, boolean selected) {
        return color(sector, x, y, selected, true);
    }

    private static int color(SelectorWheelModel.Sector sector, double x, double y,
                             boolean selected, boolean cleanFace) {
        if (!sector.contains(x, y, 0)) return 0;
        if (!sector.contains(x, y, 1)) return selected ? 0xFF000000 | RalleTheme.ACCENT_RGB : 0xFFFFFFFF;
        if (!cleanFace && !sector.contains(x + 1, y + 2, 0)) return 0xFF030A18;
        if (!cleanFace && !sector.contains(x - 1, y - 1, 0)) return 0xFF71819B;
        return selected ? 0xF0223552 : 0xEE0A1830;
    }
    private record Face(SelectorWheelModel.Sector sector, boolean selected, boolean cleanFace) {}
    private record Run(int left, int right, int y, int color) {}
}
