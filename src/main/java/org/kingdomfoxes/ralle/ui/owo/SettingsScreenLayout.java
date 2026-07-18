package org.kingdomfoxes.ralle.ui.owo;

import java.util.List;

/** Pure geometry and section-tracking rules for the settings screen. */
final class SettingsScreenLayout {
    static final int SCREEN_MARGIN = 10;
    static final int PANEL_PADDING = 10;
    static final int PANEL_GAP = 7;
    static final int SIDEBAR_WIDTH = 148;
    static final int SIDEBAR_PADDING = 6;
    static final int SEARCH_HEIGHT = 20;
    static final int SIDEBAR_GAP = 5;
    static final int NAVIGATION_ROW_GAP = 3;
    static final int ACTIVE_MARKER = 10;
    static final int MINIMUM_DOCUMENT_BOTTOM_SPACE = 24;
    private static final int MAX_PANEL_WIDTH = 620;

    private SettingsScreenLayout() {}

    static Geometry calculate(int screenWidth, int screenHeight) {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(260, screenWidth - SCREEN_MARGIN * 2));
        int panelHeight = Math.max(RalleHeader.HEIGHT + PANEL_GAP + PANEL_PADDING * 2 + 1,
                screenHeight - SCREEN_MARGIN * 2);
        int bodyWidth = panelWidth - PANEL_PADDING * 2;
        int bodyHeight = panelHeight - PANEL_PADDING * 2 - RalleHeader.HEIGHT - PANEL_GAP;
        int sidebarWidth = Math.min(SIDEBAR_WIDTH, Math.max(96, bodyWidth / 3));
        int navigationHeight = Math.max(1,
                bodyHeight - SIDEBAR_PADDING * 2 - SEARCH_HEIGHT - SIDEBAR_GAP);
        int documentWidth = Math.max(1, bodyWidth - sidebarWidth - PANEL_GAP);
        return new Geometry(panelWidth, panelHeight, bodyWidth, bodyHeight, sidebarWidth, navigationHeight, documentWidth);
    }

    static int trailingDocumentSpace(int viewportHeight, int contentAfterFinalAnchor) {
        return Math.max(MINIMUM_DOCUMENT_BOTTOM_SPACE,
                viewportHeight - ACTIVE_MARKER - contentAfterFinalAnchor + MINIMUM_DOCUMENT_BOTTOM_SPACE);
    }

    static int activeSection(List<Integer> anchorOffsets, int scrollOffset, int maxScroll) {
        if (anchorOffsets.isEmpty()) return -1;
        if (maxScroll > 0 && scrollOffset >= maxScroll) return anchorOffsets.size() - 1;
        int active = 0;
        for (int index = 0; index < anchorOffsets.size(); index++) {
            if (anchorOffsets.get(index) - scrollOffset <= ACTIVE_MARKER) active = index;
        }
        return active;
    }

    record Geometry(
            int panelWidth,
            int panelHeight,
            int bodyWidth,
            int bodyHeight,
            int sidebarWidth,
            int navigationHeight,
            int documentWidth
    ) {}
}
