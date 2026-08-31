package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsScreenLayoutTest {
    @Test
    void bodyGeometryKeepsBothColumnsInsideMatchingBoundaries() {
        var narrow = SettingsScreenLayout.calculate(320, 240);
        assertEquals(300, narrow.panelWidth());
        assertEquals(220, narrow.panelHeight());
        assertEquals(narrow.bodyHeight(), narrow.panelHeight()
                - SettingsScreenLayout.PANEL_PADDING * 2
                - RalleHeader.HEIGHT
                - SettingsScreenLayout.PANEL_GAP);
        assertEquals(narrow.bodyWidth(), narrow.sidebarWidth()
                + SettingsScreenLayout.PANEL_GAP
                + narrow.documentWidth());
        assertTrue(narrow.navigationHeight() < narrow.bodyHeight());

        var wide = SettingsScreenLayout.calculate(1920, 1080);
        assertEquals(620, wide.panelWidth());
        assertEquals(1060, wide.panelHeight());
        assertEquals(wide.bodyWidth(), wide.sidebarWidth()
                + SettingsScreenLayout.PANEL_GAP
                + wide.documentWidth());
    }

    @Test
    void descriptionWidthsUseCalculatedDocumentGeometryBeforeFirstLayout() {
        var wide = SettingsScreenLayout.calculate(1920, 1080);
        assertEquals(445, wide.documentWidth());
        assertEquals(272, SettingsScreenLayout.descriptionWidth(wide.documentWidth(), false));
        assertEquals(415, SettingsScreenLayout.dependencyDescriptionWidth(wide.documentWidth()));

        var narrow = SettingsScreenLayout.calculate(320, 240);
        assertEquals(177, narrow.documentWidth());
        assertEquals(150, SettingsScreenLayout.descriptionWidth(narrow.documentWidth(), true));
        assertEquals(147, SettingsScreenLayout.dependencyDescriptionWidth(narrow.documentWidth()));
    }

    @Test
    void anchoredScrollKeepsRebuiltControlAtItsViewportPosition() {
        assertEquals(180, SettingsScreenLayout.anchoredScrollOffset(220, 40, 500));
        assertEquals(0, SettingsScreenLayout.anchoredScrollOffset(20, 40, 500));
        assertEquals(500, SettingsScreenLayout.anchoredScrollOffset(620, 40, 500));
    }

}
