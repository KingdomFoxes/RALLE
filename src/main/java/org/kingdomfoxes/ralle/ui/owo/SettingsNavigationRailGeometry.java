package org.kingdomfoxes.ralle.ui.owo;

import java.util.ArrayList;
import java.util.List;

/** Pure row classification and pixel geometry for the connected settings navigation rail. */
final class SettingsNavigationRailGeometry {
    static final int THICKNESS = 2;
    static final int CATEGORY_LANE_X = 0;
    static final int SUBCATEGORY_LANE_X = 15;

    private SettingsNavigationRailGeometry() {}

    static List<Shape> shapes(List<Level> levels) {
        var shapes = new ArrayList<Shape>(levels.size());
        for (int index = 0; index < levels.size(); index++) {
            var level = levels.get(index);
            var next = index + 1 < levels.size() ? levels.get(index + 1) : null;
            if (level == Level.CATEGORY) {
                shapes.add(next == Level.SUBCATEGORY ? Shape.ENTER_SUBCATEGORIES : Shape.CATEGORY);
            } else if (next == Level.CATEGORY) {
                shapes.add(Shape.EXIT_SUBCATEGORIES);
            } else if (next == null) {
                shapes.add(Shape.END_SUBCATEGORIES);
            } else {
                shapes.add(Shape.SUBCATEGORY);
            }
        }
        return List.copyOf(shapes);
    }

    static List<Segment> segments(Shape shape, int rowWidth, int visualHeight, int totalHeight) {
        if (rowWidth < 1 || visualHeight < THICKNESS || totalHeight < visualHeight) {
            throw new IllegalArgumentException("Navigation rail dimensions must contain the visible row");
        }
        var segments = new ArrayList<Segment>(3);
        int turnY = visualHeight - THICKNESS;
        switch (shape) {
            case CATEGORY -> segments.add(vertical(CATEGORY_LANE_X, 0, totalHeight));
            case ENTER_SUBCATEGORIES -> {
                segments.add(vertical(CATEGORY_LANE_X, 0, visualHeight));
                segments.add(horizontal(CATEGORY_LANE_X, SUBCATEGORY_LANE_X + THICKNESS, turnY));
                segments.add(vertical(SUBCATEGORY_LANE_X, turnY, totalHeight));
            }
            case SUBCATEGORY -> segments.add(vertical(SUBCATEGORY_LANE_X, 0, totalHeight));
            case EXIT_SUBCATEGORIES -> {
                segments.add(vertical(SUBCATEGORY_LANE_X, 0, visualHeight));
                segments.add(horizontal(CATEGORY_LANE_X, SUBCATEGORY_LANE_X + THICKNESS, turnY));
                segments.add(vertical(CATEGORY_LANE_X, turnY, totalHeight));
            }
            case END_SUBCATEGORIES -> {
                segments.add(vertical(SUBCATEGORY_LANE_X, 0, visualHeight));
                segments.add(horizontal(SUBCATEGORY_LANE_X, rowWidth, turnY));
            }
        }
        return List.copyOf(segments);
    }

    private static Segment vertical(int x, int top, int bottom) {
        return new Segment(x, top, THICKNESS, bottom - top);
    }

    private static Segment horizontal(int left, int right, int y) {
        return new Segment(left, y, right - left, THICKNESS);
    }

    enum Level { CATEGORY, SUBCATEGORY }

    enum Shape { CATEGORY, ENTER_SUBCATEGORIES, SUBCATEGORY, EXIT_SUBCATEGORIES, END_SUBCATEGORIES }

    record Segment(int x, int y, int width, int height) {}
}
