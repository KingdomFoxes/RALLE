package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
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
        Treatment treatment = "text".equals(registry.setting(RalleSettings.EFFECT_TREATMENT_ID,
                ChoiceSetting.class).value()) ? Treatment.TEXT : Treatment.PLATE;
        int outline = registry.setting(RalleSettings.WHITE_USERNAME_OUTLINE_ID, BooleanSetting.class).value()
                ? Integer.parseInt(registry.setting(RalleSettings.OUTLINE_THICKNESS_ID, ChoiceSetting.class).value()) : 0;
        return new CosmeticAppearance(resolution, treatment, outline);
    }
}
