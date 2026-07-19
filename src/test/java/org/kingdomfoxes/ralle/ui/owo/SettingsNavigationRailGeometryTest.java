package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kingdomfoxes.ralle.ui.owo.SettingsNavigationRailGeometry.Level.CATEGORY;
import static org.kingdomfoxes.ralle.ui.owo.SettingsNavigationRailGeometry.Level.SUBCATEGORY;
import static org.kingdomfoxes.ralle.ui.owo.SettingsNavigationRailGeometry.Shape.END_SUBCATEGORIES;
import static org.kingdomfoxes.ralle.ui.owo.SettingsNavigationRailGeometry.Shape.ENTER_SUBCATEGORIES;
import static org.kingdomfoxes.ralle.ui.owo.SettingsNavigationRailGeometry.Shape.EXIT_SUBCATEGORIES;

class SettingsNavigationRailGeometryTest {
    @Test
    void subcategoryHighlightBeginsAfterTheGoldRail() {
        int buttonLeftMargin = 8;
        int inset = SettingsNavigationRailGeometry.highlightLeftInset(SUBCATEGORY, buttonLeftMargin);
        assertEquals(9, inset);
        assertEquals(
                SettingsNavigationRailGeometry.SUBCATEGORY_LANE_X + SettingsNavigationRailGeometry.THICKNESS,
                buttonLeftMargin + inset
        );
        assertEquals(0, SettingsNavigationRailGeometry.highlightLeftInset(CATEGORY, 0));
    }

    @Test
    void collapsedNavigationStaysInTheCategoryLaneWithoutABottomCap() {
        assertEquals(
                List.of(
                        SettingsNavigationRailGeometry.Shape.CATEGORY,
                        SettingsNavigationRailGeometry.Shape.CATEGORY,
                        SettingsNavigationRailGeometry.Shape.CATEGORY
                ),
                SettingsNavigationRailGeometry.shapes(List.of(CATEGORY, CATEGORY, CATEGORY))
        );
        assertEquals(
                List.of(new SettingsNavigationRailGeometry.Segment(0, 0, 2, 20)),
                SettingsNavigationRailGeometry.segments(SettingsNavigationRailGeometry.Shape.CATEGORY, 120, 20, 20)
        );
    }

    @Test
    void expandedMiddleCategoryEntersAndReturnsFromTheSubcategoryLane() {
        assertEquals(
                List.of(
                        SettingsNavigationRailGeometry.Shape.CATEGORY,
                        ENTER_SUBCATEGORIES,
                        SettingsNavigationRailGeometry.Shape.SUBCATEGORY,
                        EXIT_SUBCATEGORIES,
                        SettingsNavigationRailGeometry.Shape.CATEGORY
                ),
                SettingsNavigationRailGeometry.shapes(List.of(
                        CATEGORY, CATEGORY, SUBCATEGORY, SUBCATEGORY, CATEGORY
                ))
        );
        assertEquals(
                List.of(
                        new SettingsNavigationRailGeometry.Segment(0, 0, 2, 20),
                        new SettingsNavigationRailGeometry.Segment(0, 18, 17, 2),
                        new SettingsNavigationRailGeometry.Segment(15, 18, 2, 5)
                ),
                SettingsNavigationRailGeometry.segments(ENTER_SUBCATEGORIES, 120, 20, 23)
        );
        assertEquals(
                List.of(
                        new SettingsNavigationRailGeometry.Segment(15, 0, 2, 18),
                        new SettingsNavigationRailGeometry.Segment(0, 16, 17, 2),
                        new SettingsNavigationRailGeometry.Segment(0, 16, 2, 5)
                ),
                SettingsNavigationRailGeometry.segments(EXIT_SUBCATEGORIES, 120, 18, 21)
        );
    }

    @Test
    void expandedFinalCategoryCapsBeneathItsLastSubcategory() {
        assertEquals(
                List.of(
                        SettingsNavigationRailGeometry.Shape.CATEGORY,
                        ENTER_SUBCATEGORIES,
                        SettingsNavigationRailGeometry.Shape.SUBCATEGORY,
                        END_SUBCATEGORIES
                ),
                SettingsNavigationRailGeometry.shapes(List.of(
                        CATEGORY, CATEGORY, SUBCATEGORY, SUBCATEGORY
                ))
        );
        assertEquals(
                List.of(
                        new SettingsNavigationRailGeometry.Segment(15, 0, 2, 18),
                        new SettingsNavigationRailGeometry.Segment(15, 16, 97, 2)
                ),
                SettingsNavigationRailGeometry.segments(END_SUBCATEGORIES, 120, 18, 18)
        );
    }

    @Test
    void expandedFirstCategoryUsesTheSameEnterAndReturnShapes() {
        assertEquals(
                List.of(ENTER_SUBCATEGORIES, EXIT_SUBCATEGORIES, SettingsNavigationRailGeometry.Shape.CATEGORY),
                SettingsNavigationRailGeometry.shapes(List.of(CATEGORY, SUBCATEGORY, CATEGORY))
        );
    }
}
