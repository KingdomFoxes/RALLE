package org.kingdomfoxes.ralle.ui.owo;

import java.util.ArrayList;
import java.util.List;

/** Geometry and stable animation envelopes; option order never represents a persistent player identity. */
final class SelectorWheelLayout {
    private SelectorWheelLayout() {}

    interface Face {
        double centerX();
        double centerY();
        double motionX();
        double motionY();
        int left();
        int top();
        int right();
        int bottom();
        boolean contains(double x, double y);
    }

    record Circle(SelectorWheelModel.Sector sector) implements Face {
        public double centerX() { return sector.centerX(); }
        public double centerY() { return sector.centerY(); }
        public double motionX() { return Math.cos(sector.angle()); }
        public double motionY() { return Math.sin(sector.angle()); }
        public int left() { return -(int) Math.ceil(sector.outer()); }
        public int top() { return left(); }
        public int right() { return -left(); }
        public int bottom() { return right(); }
        public boolean contains(double x, double y) {
            for (int offset = 0; offset <= SelectorWheelModel.EXTENSION; offset++)
                if (sector.contains(x - motionX() * offset, y - motionY() * offset, 0)) return true;
            return false;
        }
    }

    record Bar(int left, int top, int right, int bottom) implements Face {
        public double centerX() { return (left + right) / 2d; }
        public double centerY() { return (top + bottom) / 2d; }
        public double motionX() { return 0; }
        public double motionY() { return -1; }
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top - SelectorWheelModel.EXTENSION && y < bottom;
        }
    }

    static List<Face> circles(List<SelectorWheelModel.Sector> sectors) {
        return sectors.stream().<Face>map(Circle::new).toList();
    }

    /** S is the original square side: height is 55% of S, width stays at least S/5, with shared borders. */
    static List<Face> bars(int count, int side, int viewportWidth) {
        if (count < 0 || side < 1 || viewportWidth < 1) throw new IllegalArgumentException("Invalid bar layout");
        if (count == 0) return List.of();
        int cap = Math.max(1, (int) Math.floor(viewportWidth * .64));
        side = Math.min(side, cap);
        int barHeight = Math.max(1, (int) Math.round(side * .55));
        if (count <= 5) {
            var result = new ArrayList<Face>();
            int left = -side / 2;
            for (int i = 0; i < count; i++)
                result.add(new Bar(left + i * side / count, -barHeight / 2,
                        left + (i + 1) * side / count, -barHeight / 2 + barHeight));
            return List.copyOf(result);
        }
        int columns = Math.min(count, 5);
        // Use integer edges without ever rounding the minimum down below 20% of S.
        int barWidth = (int) Math.ceil(side / (double) columns);
        int perRow = Math.max(1, cap / barWidth);
        int rows = (count + perRow - 1) / perRow;
        int totalHeight = rows * barHeight + (rows - 1) * 8;
        var result = new ArrayList<Face>();
        for (int start = 0, row = 0; start < count; start += perRow, row++) {
            int size = Math.min(perRow, count - start);
            int left = -size * barWidth / 2;
            int top = -totalHeight / 2 + row * (barHeight + 8);
            for (int column = 0; column < size; column++)
                result.add(new Bar(left + column * barWidth, top, left + (column + 1) * barWidth, top + barHeight));
        }
        return List.copyOf(result);
    }

    static int hit(List<Face> faces, double x, double y) {
        for (int i = 0; i < faces.size(); i++) if (faces.get(i).contains(x, y)) return i;
        return -1;
    }
}
