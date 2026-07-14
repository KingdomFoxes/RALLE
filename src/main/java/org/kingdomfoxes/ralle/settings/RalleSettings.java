package org.kingdomfoxes.ralle.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.ActionEntry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.List;

public final class RalleSettings {
    private RalleSettings() {}

    public static void register(SettingsRegistry registry) {
        registry.registerCategory(new SettingsCategory(
                "chat",
                Component.translatable("ralle.settings.category.chat"),
                Component.translatable("ralle.settings.category.chat.description"),
                List.of(
                        toggle("chat-enabled"),
                        action("edit-chat-layout"),
                        toggle("compact-chat"),
                        toggle("stack-empty-lines"),
                        toggle("message-direction-enabled"),
                        choice("message-direction", "bottom-up", "bottom-up", "top-down"),
                        toggle("horizontal-alignment-enabled"),
                        choice("horizontal-alignment", "left", "left", "right"),
                        toggle("text-shadow-enabled"),
                        choice("text-shadow", "vanilla", "none", "vanilla", "full", "wrapped-full")
                )
        ));

        registry.registerCategory(new SettingsCategory(
                "raid-lfg",
                Component.translatable("ralle.settings.category.raid-lfg"),
                Component.translatable("ralle.settings.category.raid-lfg.description"),
                List.of(
                        toggle("raid-lfg-enabled"),
                        toggle("notification-sounds")
                )
        ));
    }

    private static BooleanSetting toggle(String id) {
        return new BooleanSetting(id, title(id), description(id));
    }

    private static ActionEntry action(String id) {
        return new ActionEntry(id, title(id), description(id));
    }

    private static ChoiceSetting choice(String id, String defaultValue, String... choices) {
        return new ChoiceSetting(id, title(id), description(id), defaultValue, List.of(choices));
    }

    private static Component title(String id) {
        return Component.translatable("ralle.settings.option." + id);
    }

    private static Component description(String id) {
        return Component.translatable("ralle.settings.option." + id + ".description");
    }
}
