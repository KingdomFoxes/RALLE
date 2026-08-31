package org.kingdomfoxes.ralle.ui.owo;

/** Pure geometry and accessible-state projection for the settings toggle. */
final class RalleTogglePresentation {
    static final int CONTROL_LANE_WIDTH = 126;
    static final int TRACK_WIDTH = 64;
    static final int TRACK_HEIGHT = 14;
    static final int CONTROL_HEIGHT = 20;
    static final int THUMB_WIDTH = 14;
    static final int THUMB_HEIGHT = 18;

    private RalleTogglePresentation() {}

    static int thumbLeft(int trackX, int trackWidth, boolean enabled) {
        return enabled ? trackX + trackWidth - THUMB_WIDTH - 2 : trackX + 2;
    }

    static int thumbTop(int controlY, int controlHeight) {
        return controlY + (controlHeight - THUMB_HEIGHT) / 2;
    }

    static String stateTranslationKey(boolean enabled) {
        return enabled ? "ralle.settings.enabled" : "ralle.settings.disabled";
    }
}
