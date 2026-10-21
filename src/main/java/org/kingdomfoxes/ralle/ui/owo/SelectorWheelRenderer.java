package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.ui.theme.RallePalette;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Screen-owned, nearest-filtered face images. One quad per circle segment, including hover. */
final class SelectorWheelRenderer implements AutoCloseable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final Map<SelectorWheelModel.Sector, FaceTexture> textures = new HashMap<>();
    private long paletteRevision = Long.MIN_VALUE;

    /** Prepare both color states once, outside the animation loop. Never evict a visible option. */
    void prepare(List<SelectorWheelLayout.Face> faces) {
        close();
        paletteRevision = RallePalette.revision();
        for (var face : faces) if (face instanceof SelectorWheelLayout.Circle circle)
            texture(circle.sector());
    }

    void drawFace(GuiGraphics graphics, SelectorWheelLayout.Face face, int dx, int dy, boolean selected) {
        if (face instanceof SelectorWheelLayout.Circle circle) {
            draw(graphics, circle.sector(), dx, dy, selected);
            return;
        }
        int left = face.left() + dx, top = face.top() + dy;
        int right = face.right() + dx, bottom = face.bottom() + dy;
        graphics.fill(left, top, right, bottom, outlineColor(selected));
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fillColor(selected));
    }

    private void draw(GuiGraphics graphics, SelectorWheelModel.Sector sector, int dx, int dy, boolean selected) {
        if (paletteRevision != RallePalette.revision()) {
            close();
            paletteRevision = RallePalette.revision();
        }
        var face = texture(sector);
        graphics.blit(RenderPipelines.GUI_TEXTURED, face.id(), face.left() + dx, face.top() + dy,
                selected ? face.width() : 0, 0, face.width(), face.height(),
                face.width() * 2, face.height());
    }

    private FaceTexture texture(SelectorWheelModel.Sector sector) {
        return textures.computeIfAbsent(sector, key -> {
            var raster = rasterize(key);
            var pixels = new NativeImage(raster.width() * 2, raster.height(), true);
            int[] normal = {0, outlineColor(false), fillColor(false)};
            int[] selected = {0, outlineColor(true), fillColor(true)};
            for (int y = 0; y < raster.height(); y++) for (int x = 0; x < raster.width(); x++) {
                int mask = raster.mask()[y * raster.width() + x];
                pixels.setPixel(x, y, normal[mask]);
                pixels.setPixel(x + raster.width(), y, selected[mask]);
            }
            var id = Identifier.fromNamespaceAndPath("ralle", "dynamic/wheel_face_" + NEXT_ID.incrementAndGet());
            var texture = new FaceImage(pixels);
            try {
                Minecraft.getInstance().getTextureManager().register(id, texture);
                return new FaceTexture(id, raster.left(), raster.top(), raster.width(), raster.height());
            } catch (RuntimeException | Error failure) {
                texture.close();
                throw failure;
            }
        });
    }

    /** Crop to the sector's angular/radial envelope so outer rings do not allocate full-wheel images. */
    static Raster rasterize(SelectorWheelModel.Sector sector) {
        double minX = Double.POSITIVE_INFINITY, minY = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        double start = sector.angle() - sector.halfAngle(), end = sector.angle() + sector.halfAngle();
        // Coordinate extrema occur at an arc endpoint or a cardinal angle, on either radius.
        var angles = new java.util.ArrayList<Double>(List.of(start, end));
        for (int i = (int) Math.ceil(start / (Math.PI / 2)); i <= Math.floor(end / (Math.PI / 2)); i++)
            angles.add(i * Math.PI / 2);
        for (double angle : angles) for (double radius : new double[] {sector.inner(), sector.outer()}) {
            double x = Math.cos(angle) * radius, y = Math.sin(angle) * radius;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
        int width = Math.max(1, (int) Math.ceil(maxX) - left);
        int height = Math.max(1, (int) Math.ceil(maxY) - top);
        byte[] mask = new byte[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double px = left + x + .5, py = top + y + .5;
            if (sector.contains(px, py, 0)) {
                mask[y * width + x] = (byte) (sector.contains(px, py, 1) ? 2 : 1);
            }
        }
        return new Raster(left, top, width, height, mask);
    }

    @Override public void close() {
        textures.values().forEach(face -> Minecraft.getInstance().getTextureManager().release(face.id()));
        textures.clear();
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
    record Raster(int left, int top, int width, int height, byte[] mask) {
        int colorAt(int x, int y, boolean selected) {
            return switch (mask[y * width + x]) {
                case 1 -> outlineColor(selected);
                case 2 -> fillColor(selected);
                default -> 0;
            };
        }
    }
    private record FaceTexture(Identifier id, int left, int top, int width, int height) {}
    private static final class FaceImage extends DynamicTexture {
        FaceImage(NativeImage pixels) {
            super(() -> "RALLE selector face", pixels);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }
    }
}
