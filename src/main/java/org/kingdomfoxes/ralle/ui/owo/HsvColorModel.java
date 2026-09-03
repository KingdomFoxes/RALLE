package org.kingdomfoxes.ralle.ui.owo;

import java.awt.Color;

/** Pure HSV conversion and wheel/triangle hit geometry shared by color editors. */
public final class HsvColorModel {
    private HsvColorModel() {}

    public static Hsv fromRgb(int rgb) {
        var values = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return new Hsv(values[0], values[1], values[2]);
    }

    public static int toRgb(Hsv hsv) {
        return Color.HSBtoRGB(hsv.hue(), hsv.saturation(), hsv.value()) & 0xFFFFFF;
    }

    public static Hit hit(double x, double y, int size) {
        double center = size / 2d;
        double dx = x - center;
        double dy = y - center;
        double radius = Math.hypot(dx, dy);
        double outer = outerRadius(size);
        double inner = innerRadius(size);
        if (radius >= inner && radius <= outer) return Hit.WHEEL;
        return insideTriangle(x, y, size) ? Hit.TRIANGLE : Hit.NONE;
    }

    public static boolean insideTriangle(double x, double y, int size) {
        var weights = barycentric(x, y, triangle(size));
        return weights.hue() >= 0 && weights.white() >= 0 && weights.black() >= 0;
    }

    static Hsv updateWheel(Hsv current, double x, double y, int size) {
        double angle = Math.atan2(y - size / 2d, x - size / 2d);
        float hue = (float) ((angle / (Math.PI * 2) + 1.25) % 1d);
        return new Hsv(hue, current.saturation(), current.value());
    }

    static Hsv updateTriangle(Hsv current, double x, double y, int size) {
        var weights = barycentric(x, y, triangle(size)).clamped();
        float value = (float) Math.clamp(weights.hue() + weights.white(), 0, 1);
        float saturation = value <= .0001f ? 0 : (float) Math.clamp(weights.hue() / value, 0, 1);
        return new Hsv(current.hue(), saturation, value);
    }

    static SelectionPoint triangleSelection(Hsv hsv, int size) {
        var triangle = triangle(size);
        double hueWeight = hsv.saturation() * hsv.value();
        double whiteWeight = (1 - hsv.saturation()) * hsv.value();
        double blackWeight = 1 - hsv.value();
        return new SelectionPoint(
                triangle.hue().x() * hueWeight
                        + triangle.white().x() * whiteWeight
                        + triangle.black().x() * blackWeight,
                triangle.hue().y() * hueWeight
                        + triangle.white().y() * whiteWeight
                        + triangle.black().y() * blackWeight
        );
    }

    static Triangle triangle(int size) {
        double center = size / 2d;
        double radius = innerRadius(size);
        return new Triangle(
                point(center, center, radius, -Math.PI / 2),
                point(center, center, radius, -Math.PI / 2 - Math.PI * 2 / 3),
                point(center, center, radius, -Math.PI / 2 + Math.PI * 2 / 3)
        );
    }

    static double outerRadius(int size) {
        return Math.max(1, size / 2d - .5);
    }

    static double coloredOuterRadius(int size) {
        return Math.max(1, outerRadius(size) - 2);
    }

    static double innerRadius(int size) {
        return coloredOuterRadius(size) * .72;
    }

    private static Weights barycentric(double x, double y, Triangle triangle) {
        var a = triangle.hue();
        var b = triangle.white();
        var c = triangle.black();
        double denominator = (b.y() - c.y()) * (a.x() - c.x())
                + (c.x() - b.x()) * (a.y() - c.y());
        double hue = ((b.y() - c.y()) * (x - c.x()) + (c.x() - b.x()) * (y - c.y())) / denominator;
        double white = ((c.y() - a.y()) * (x - c.x()) + (a.x() - c.x()) * (y - c.y())) / denominator;
        return new Weights(hue, white, 1 - hue - white);
    }

    private static Point point(double cx, double cy, double radius, double angle) {
        return new Point(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
    }

    public enum Hit { NONE, WHEEL, TRIANGLE }

    public record Hsv(float hue, float saturation, float value) {
        public Hsv {
            hue = Math.clamp(hue, 0, 1);
            saturation = Math.clamp(saturation, 0, 1);
            value = Math.clamp(value, 0, 1);
        }
    }

    record SelectionPoint(double x, double y) {}
    record Triangle(Point hue, Point white, Point black) {}
    record Point(double x, double y) {}

    private record Weights(double hue, double white, double black) {
        Weights clamped() {
            double nextHue = Math.max(0, hue);
            double nextWhite = Math.max(0, white);
            double nextBlack = Math.max(0, black);
            double total = nextHue + nextWhite + nextBlack;
            return total == 0
                    ? new Weights(0, 0, 1)
                    : new Weights(nextHue / total, nextWhite / total, nextBlack / total);
        }
    }
}
