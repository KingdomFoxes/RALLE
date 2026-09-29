package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

/** Local-only viewer preferences. Fox supplies only selected style IDs and grants. */
public record CosmeticAppearance(double resolution, Treatment treatment, int usernameOutlinePixels) {
    public enum Treatment { TEXT, PLATE }

    public static CosmeticAppearance from(SettingsRegistry registry, NameplateStyle style) {
        String selectedResolution = registry.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class).value();
        double resolution = "recipe".equals(selectedResolution) ? style.defaultResolution()
                : Double.parseDouble(selectedResolution);
        // Removed viewer controls always resolve to whole-plate material without a white outline.
        return new CosmeticAppearance(resolution, Treatment.PLATE, 0);
    }
}
