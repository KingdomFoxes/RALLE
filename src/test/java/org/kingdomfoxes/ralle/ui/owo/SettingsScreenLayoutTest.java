package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void calculatedTrailingSpaceLetsFinalDividerPassTheActiveMarker() {
        int viewport = 200;
        int anchor = 500;
        int contentAfterAnchor = 60;
        int trailing = SettingsScreenLayout.trailingDocumentSpace(viewport, contentAfterAnchor);
        int maximumScroll = anchor + contentAfterAnchor + trailing - viewport;
        assertTrue(anchor - maximumScroll <= SettingsScreenLayout.ACTIVE_MARKER);
        assertTrue(trailing >= SettingsScreenLayout.MINIMUM_DOCUMENT_BOTTOM_SPACE);
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
    void jumpOffsetAlignsClickedDividerWithActiveMarkerAndIsIdempotent() {
        var anchors = List.of(14, 134, 254);
        int target = SettingsScreenLayout.jumpScrollOffset(anchors.get(1), 300);
        assertEquals(120, target);
        assertEquals(1, SettingsScreenLayout.activeSection(anchors, target, 300));
        assertEquals(target, SettingsScreenLayout.jumpScrollOffset(anchors.get(1), 300));

        assertEquals(0, SettingsScreenLayout.jumpScrollOffset(anchors.getFirst(), 300));
        assertEquals(300, SettingsScreenLayout.jumpScrollOffset(500, 300));
        assertEquals(2, SettingsScreenLayout.activeSection(anchors, 300, 300));
    }

    @Test
    void activeSectionTracksAnchorsAndForcesLastAtMaximumScroll() {
        var anchors = List.of(0, 120, 240);
        assertEquals(0, SettingsScreenLayout.activeSection(anchors, 0, 260));
        assertEquals(1, SettingsScreenLayout.activeSection(anchors, 130, 260));
        assertEquals(2, SettingsScreenLayout.activeSection(anchors, 260, 260));
    }
}
