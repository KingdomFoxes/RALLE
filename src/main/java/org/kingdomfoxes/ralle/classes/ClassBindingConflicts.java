package org.kingdomfoxes.ralle.classes;

import org.kingdomfoxes.ralle.api.settings.*;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import java.util.List;

/** One conflict policy shared by settings feedback and both class/LFG input owners. */
public final class ClassBindingConflicts {
    private ClassBindingConflicts() {}
    public static List<String> reserved(SettingsRegistry settings) {
        return settings.entries().stream().filter(KeybindSetting.class::isInstance).map(KeybindSetting.class::cast)
                .filter(s -> !s.id().equals(RalleSettings.CLASS_MODIFIER_ID)).map(KeybindSetting::value).toList();
    }
    public static boolean modifier(SettingsRegistry settings) {
        String binding = settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class).value();
        return !KeybindSetting.UNBOUND.equals(binding) && reserved(settings).contains(binding);
    }
    public static boolean action(SettingsRegistry settings, String binding) {
        return settings.setting(RalleSettings.CLASS_SELECTOR_ID, BooleanSetting.class).value()
                && !KeybindSetting.UNBOUND.equals(binding)
                && binding.equals(settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class).value());
    }
}
