package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.NativeImage;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Reusable wheel-and-triangle HSV picker with RGB output. */
public final class HsvWheelTrianglePicker extends BaseUIComponent {
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();
    private static final double TRIANGLE_OUTLINE_RADIUS = .9;

    private final int pickerSize;
    private final Identifier textureId;
    private final DynamicTexture texture;
    private HsvColorModel.Hsv hsv;
    private HsvColorModel.Hit dragging = HsvColorModel.Hit.NONE;
    private IntConsumer changed = ignored -> {};

    public HsvWheelTrianglePicker(int size, int rgb) {
        sizing(Sizing.fixed(size));
        pickerSize = size;
        hsv = HsvColorModel.fromRgb(rgb);
        var pixels = new NativeImage(size, size, true);
        renderTexture(pixels);
        texture = new DynamicTexture(() -> "RALLE HSV color picker", pixels);
        textureId = Identifier.fromNamespaceAndPath(
                "ralle", "dynamic/hsv_picker_" + NEXT_TEXTURE_ID.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
    }

    public HsvWheelTrianglePicker onChanged(IntConsumer listener) {
        changed = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public void rgb(int rgb) {
        var next = HsvColorModel.fromRgb(rgb);
        boolean hueChanged = Float.compare(hsv.hue(), next.hue()) != 0;
        hsv = next;
        if (hueChanged) refreshTexture();
    }

    public int rgb() {
        return HsvColorModel.toRgb(hsv);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int size = Math.min(width, height);
        double center = size / 2d;
        double outer = HsvColorModel.outerRadius(size);
        double inner = HsvColorModel.innerRadius(size);
        graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0, 0,
                size, size, pickerSize, pickerSize);
        double wheelAngle = (hsv.hue() - .25) * Math.PI * 2;
        int markerX = x + (int) Math.round(center + Math.cos(wheelAngle) * ((inner + outer) / 2));
        int markerY = y + (int) Math.round(center + Math.sin(wheelAngle) * ((inner + outer) / 2));
        graphics.drawRectOutline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        var triangleMarker = HsvColorModel.triangleSelection(hsv, size);
        int triangleX = x + (int) Math.round(triangleMarker.x());
        int triangleY = y + (int) Math.round(triangleMarker.y());
        graphics.drawRectOutline(triangleX - 2, triangleY - 2, 5, 5, 0xFF080D16);
        graphics.drawRectOutline(triangleX - 3, triangleY - 3, 7, 7, 0xFFFFFFFF);
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        dragging = HsvColorModel.hit(click.x(), click.y(), Math.min(width, height));
        update(click.x(), click.y());
        return dragging != HsvColorModel.Hit.NONE || super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        update(click.x(), click.y());
        return dragging != HsvColorModel.Hit.NONE || super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        dragging = HsvColorModel.Hit.NONE;
        return super.onMouseUp(click);
    }

    @Override public boolean canFocus(FocusSource source) { return true; }
    @Override public CursorStyle cursorStyle() { return CursorStyle.CROSSHAIR; }

    @Override
    public void dismount(DismountReason reason) {
        super.dismount(reason);
        if (reason == DismountReason.REMOVED) {
            Minecraft.getInstance().getTextureManager().release(textureId);
        }
    }

    private void update(double localX, double localY) {
        float previousHue = hsv.hue();
        if (dragging == HsvColorModel.Hit.WHEEL) hsv = HsvColorModel.updateWheel(hsv, localX, localY, Math.min(width, height));
        else if (dragging == HsvColorModel.Hit.TRIANGLE) hsv = HsvColorModel.updateTriangle(hsv, localX, localY, Math.min(width, height));
        else return;
        if (Float.compare(previousHue, hsv.hue()) != 0) refreshTexture();
        changed.accept(rgb());
    }

    private void refreshTexture() {
        var pixels = texture.getPixels();
        if (pixels == null) return;
        renderTexture(pixels);
        texture.upload();
    }

    private void renderTexture(NativeImage pixels) {
        double center = pickerSize / 2d;
        double outer = HsvColorModel.outerRadius(pickerSize);
        double coloredOuter = HsvColorModel.coloredOuterRadius(pickerSize);
        double inner = HsvColorModel.innerRadius(pickerSize);
        var triangle = HsvColorModel.triangle(pickerSize);
        for (int yy = 0; yy < pickerSize; yy++) {
            for (int xx = 0; xx < pickerSize; xx++) {
                double sampleX = xx + .5;
                double sampleY = yy + .5;
                double dx = sampleX - center;
                double dy = sampleY - center;
                double radius = Math.hypot(dx, dy);
                int color = 0;
                if (radius <= outer && radius > coloredOuter) {
                    color = 0xFFFFFFFF;
                } else if (radius <= coloredOuter && radius >= inner + 1) {
                    double angle = Math.atan2(dy, dx);
                    float hue = (float) ((angle / (Math.PI * 2) + 1.25) % 1d);
                    color = 0xFF000000 | (Color.HSBtoRGB(hue, 1, 1) & 0xFFFFFF);
                } else if (radius >= inner - 1 && radius < inner + 1) {
                    color = 0xFFFFFFFF;
                }

                double edgeDistance = Math.min(
                        distanceToSegment(sampleX, sampleY, triangle.hue(), triangle.white()),
                        Math.min(
                                distanceToSegment(sampleX, sampleY, triangle.white(), triangle.black()),
                                distanceToSegment(sampleX, sampleY, triangle.black(), triangle.hue())
                        )
                );
                if (edgeDistance <= TRIANGLE_OUTLINE_RADIUS) {
                    color = 0xFFFFFFFF;
                } else if (HsvColorModel.insideTriangle(sampleX, sampleY, pickerSize)) {
                    var sample = HsvColorModel.updateTriangle(hsv, sampleX, sampleY, pickerSize);
                    color = 0xFF000000 | HsvColorModel.toRgb(sample);
                }
                pixels.setPixel(xx, yy, color);
            }
        }
    }

    private static double distanceToSegment(double x, double y, HsvColorModel.Point start, HsvColorModel.Point end) {
        double segmentX = end.x() - start.x();
        double segmentY = end.y() - start.y();
        double lengthSquared = segmentX * segmentX + segmentY * segmentY;
        double progress = lengthSquared == 0 ? 0 : Math.clamp(
                ((x - start.x()) * segmentX + (y - start.y()) * segmentY) / lengthSquared, 0, 1);
        return Math.hypot(x - (start.x() + progress * segmentX), y - (start.y() + progress * segmentY));
    }
}
