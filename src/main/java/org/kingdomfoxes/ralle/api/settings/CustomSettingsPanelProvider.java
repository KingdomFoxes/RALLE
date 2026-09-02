package org.kingdomfoxes.ralle.api.settings;

/** Presentation bridge for custom settings panels without coupling registry metadata to a UI toolkit. */
public interface CustomSettingsPanelProvider<C, R> {
    String id();

    R render(C context);
}
