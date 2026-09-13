package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryLabelLayoutTest {
    @Test
    void placesQueueEstimateBelowHeadquartersCrown() {
        var layout = TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(10, 20, 60, 64), 8, 24, 9, true, true).orElseThrow();
        assertEquals(40, layout.centerX());
        assertEquals(33, layout.upperY());
        assertEquals(61, layout.lowerY());
    }

    @Test
    void headquartersQueueEstimateCanOverflowSmallTerritory() {
        var layout = TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(10, 20, 20, 10), 8, 24, 9, true, true).orElseThrow();
        assertEquals(20, layout.centerX());
        assertEquals(35, layout.lowerY());
    }

    @Test
    void placesLabelsAroundTagWhenAllOwnedContentHasRoom() {
        var layout = TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(10, 20, 60, 64), 8, 24, 9, false, true).orElseThrow();
        assertEquals(40, layout.centerX());
        assertTrue(layout.upperY() >= 30);
        assertTrue(layout.lowerY() > layout.upperY());
        assertEquals(35, layout.upperY());
        assertEquals(59, layout.lowerY());
    }

    @Test
    void neverShrinksTextToFitNarrowTerritories() {
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 42, 64), 72, 24, 9, false, true).isPresent());
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 42, 64), 8, 24, 9, false, true).isPresent());
    }

    @Test
    void rendersEvenWhenLabelsOverlapNamesOrOverflowTerritoryBounds() {
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 20, 20), 30, 0, 9, false, false).isPresent());
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 60, 25), 8, 24, 9, false, true).isPresent());
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 60, 28), 8, 0, 9, true, false).isPresent());
        assertTrue(TerritoryLabelLayout.calculate(
                new TerritoryRenderBounds(0, 0, 0.1F, 0.1F), 8, 24, 9, false, true).isPresent());
    }
}
