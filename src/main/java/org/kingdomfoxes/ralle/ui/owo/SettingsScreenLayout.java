package org.kingdomfoxes.ralle.ui.owo;

/** Pure geometry rules for the settings screen. */
final class SettingsScreenLayout {
    static final int SCREEN_MARGIN = 10;
    static final int PANEL_PADDING = 10;
    static final int PANEL_GAP = 7;
    static final int SIDEBAR_WIDTH = 148;
    static final int SIDEBAR_PADDING = 6;
    static final int SEARCH_HEIGHT = 20;
    static final int SIDEBAR_GAP = 5;
    static final int NAVIGATION_ROW_GAP = 3;
    static final int DOCUMENT_PADDING = 8;
    static final int MINIMUM_DOCUMENT_BOTTOM_SPACE = 16;
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

    static int descriptionWidth(int documentWidth, boolean stacked) {
        return stacked
                ? Math.max(150, documentWidth - 30)
                : Math.max(120, documentWidth * 2 / 3 - 24);
    }

    static int dependencyDescriptionWidth(int documentWidth) {
        return Math.max(120, documentWidth - 30);
    }

    static int anchoredScrollOffset(int anchorOffset, int viewportOffset, int maxScroll) {
        return Math.clamp(anchorOffset - viewportOffset, 0, Math.max(0, maxScroll));
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
