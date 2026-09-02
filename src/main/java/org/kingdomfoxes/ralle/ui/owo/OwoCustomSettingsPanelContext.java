package org.kingdomfoxes.ralle.ui.owo;

/** Context passed only by the owo presentation bridge to registered custom panels. */
public record OwoCustomSettingsPanelContext(RalleSettingsScreen screen, int width) {
}
