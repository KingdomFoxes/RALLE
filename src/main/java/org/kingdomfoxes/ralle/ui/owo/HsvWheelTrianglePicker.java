package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.Color;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Reusable wheel-and-triangle HSV picker with RGB output. */
public final class HsvWheelTrianglePicker extends BaseUIComponent {
    private HsvColorModel.Hsv hsv;
    private HsvColorModel.Hit dragging = HsvColorModel.Hit.NONE;
    private IntConsumer changed = ignored -> {};

    public HsvWheelTrianglePicker(int size, int rgb) {
        sizing(Sizing.fixed(size));
        hsv = HsvColorModel.fromRgb(rgb);
    }

    public HsvWheelTrianglePicker onChanged(IntConsumer listener) {
        changed = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public void rgb(int rgb) {
        hsv = HsvColorModel.fromRgb(rgb);
    }

    public int rgb() {
        return HsvColorModel.toRgb(hsv);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int size = Math.min(width, height);
        double center = size / 2d;
        double outer = size / 2d - 1;
        double inner = outer * .72;
        for (int yy = 0; yy < size; yy += 2) {
            for (int xx = 0; xx < size; xx += 2) {
                double dx = xx + .5 - center;
                double dy = yy + .5 - center;
                double radius = Math.hypot(dx, dy);
                int color;
                if (radius >= inner && radius <= outer) {
                    double angle = Math.atan2(dy, dx);
                    float hue = (float) ((angle / (Math.PI * 2) + 1.25) % 1d);
                    color = 0xFF000000 | (Color.HSBtoRGB(hue, 1, 1) & 0xFFFFFF);
                } else if (HsvColorModel.insideTriangle(xx, yy, size)) {
                    var sample = HsvColorModel.updateTriangle(hsv, xx, yy, size);
                    color = 0xFF000000 | HsvColorModel.toRgb(sample);
                } else continue;
                graphics.fill(x + xx, y + yy, x + Math.min(size, xx + 2), y + Math.min(size, yy + 2), color);
            }
        }
        var triangle = HsvColorModel.triangle(size);
        drawLine(graphics, triangle.hue(), triangle.white(), 0xFFFFFFFF);
        drawLine(graphics, triangle.white(), triangle.black(), 0xFFFFFFFF);
        drawLine(graphics, triangle.black(), triangle.hue(), 0xFFFFFFFF);
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

    private void update(double localX, double localY) {
        if (dragging == HsvColorModel.Hit.WHEEL) hsv = HsvColorModel.updateWheel(hsv, localX, localY, Math.min(width, height));
        else if (dragging == HsvColorModel.Hit.TRIANGLE) hsv = HsvColorModel.updateTriangle(hsv, localX, localY, Math.min(width, height));
        else return;
        changed.accept(rgb());
    }

    private void drawLine(OwoUIGraphics graphics, HsvColorModel.Point start, HsvColorModel.Point end, int color) {
        int startX = (int) Math.round(start.x());
        int startY = (int) Math.round(start.y());
        int endX = (int) Math.round(end.x());
        int endY = (int) Math.round(end.y());
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endY - startY));
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0 : step / (double) steps;
            int lineX = x + (int) Math.round(startX + (endX - startX) * progress);
            int lineY = y + (int) Math.round(startY + (endY - startY) * progress);
            graphics.fill(lineX, lineY, lineX + 1, lineY + 1, color);
        }
    }
}
