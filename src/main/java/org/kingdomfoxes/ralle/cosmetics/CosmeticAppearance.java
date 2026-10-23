package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

/** Local-only viewer preferences. Fox supplies only selected style IDs and grants. */
public record CosmeticAppearance(double resolution) {
    public static CosmeticAppearance from(SettingsRegistry registry, NameplateStyle style) {
        String selectedResolution = registry.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class).value();
        double resolution = "recipe".equals(selectedResolution) ? style.defaultResolution()
                : Double.parseDouble(selectedResolution);
        return new CosmeticAppearance(resolution);
    }
}
