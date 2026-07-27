package org.kingdomfoxes.ralle.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.ActionEntry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsSubcategory;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;

import java.util.List;

public final class RalleSettings {
    public static final String INTERFACE_FONT_ID = "interface-font";

    private RalleSettings() {}

    public static void register(SettingsRegistry registry) {
        registry.registerCategory(new SettingsCategory(
                "about",
                Component.translatable("ralle.settings.about"),
                Component.translatable("ralle.settings.about.description"),
                List.of(subcategory("interface", choice(INTERFACE_FONT_ID, "vanilla", "vanilla", "karla")))
        ));

        registry.registerCategory(new SettingsCategory(
                "chat",
                Component.translatable("ralle.settings.category.chat"),
                Component.translatable("ralle.settings.category.chat.description"),
                List.of(
                        subcategory("general", toggle("chat-enabled"), action("edit-chat-layout")),
                        subcategory("appearance",
                                toggle("hide-chat-scrollbar"),
                                toggle("message-direction-enabled"),
                                choice("message-direction", "bottom-up", "bottom-up", "top-down"),
                                toggle("horizontal-alignment-enabled"),
                                choice("horizontal-alignment", "left", "left", "right"),
                                toggle("text-shadow-enabled"),
                                choice("text-shadow", "vanilla", "none", "vanilla", "full", "wrapped-full")
                        ),
                        subcategory("message-behavior", toggle("compact-chat"), toggle("stack-empty-lines")),
                        subcategory("screenshots",
                                toggle("chat-screenshot-enabled"),
                                toggle("chat-screenshot-smooth-expansion"),
                                toggle("chat-selection-sounds")
                        )
                )
        ));

        registry.registerCategory(new SettingsCategory(
                "raid-lfg",
                Component.translatable("ralle.settings.category.raid-lfg"),
                Component.translatable("ralle.settings.category.raid-lfg.description"),
                List.of(
                        subcategory("general", toggle("raid-lfg-enabled")),
                        subcategory("notifications",
                                toggle("new-party-notifications"),
                                toggle("reopened-party-notifications"),
                                toggle("party-status-notifications"),
                                toggle("notification-sounds"),
                                action("edit-notification-position")
                        ),
                        subcategory("controls", keybind("raid-lfg-keybind"))
                )
        ));

        requireChat(registry,
                "edit-chat-layout", "hide-chat-scrollbar", "message-direction-enabled", "horizontal-alignment-enabled",
                "text-shadow-enabled", "compact-chat", "stack-empty-lines", "chat-screenshot-enabled"
        );
        registry.requireEnabled("message-direction", "message-direction-enabled");
        registry.requireEnabled("horizontal-alignment", "horizontal-alignment-enabled");
        registry.requireEnabled("text-shadow", "text-shadow-enabled");
        registry.requireEnabled("chat-screenshot-smooth-expansion", "chat-enabled");
        registry.requireEnabled("chat-screenshot-smooth-expansion", "chat-screenshot-enabled");
        registry.requireEnabled("chat-selection-sounds", "chat-enabled");
        registry.requireEnabled("chat-selection-sounds", "chat-screenshot-enabled");
        registry.requireEnabled("new-party-notifications", "raid-lfg-enabled");
        registry.requireEnabled("reopened-party-notifications", "raid-lfg-enabled");
        registry.requireEnabled("party-status-notifications", "raid-lfg-enabled");
        registry.requireEnabled("notification-sounds", "raid-lfg-enabled");
        registry.requireEnabled("edit-notification-position", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-keybind", "raid-lfg-enabled");
    }

    private static SettingsSubcategory subcategory(String id, org.kingdomfoxes.ralle.api.settings.SettingsEntry... entries) {
        return new SettingsSubcategory(
                id,
                Component.translatable("ralle.settings.subcategory." + id),
                Component.translatable("ralle.settings.subcategory." + id + ".description"),
                List.of(entries)
        );
    }

    private static void requireChat(SettingsRegistry registry, String... ids) {
        for (var id : ids) registry.requireEnabled(id, "chat-enabled");
    }

    private static BooleanSetting toggle(String id) {
        return new BooleanSetting(id, title(id), description(id));
    }

    private static ActionEntry action(String id) {
        return new ActionEntry(id, title(id), description(id));
    }

    private static KeybindSetting keybind(String id) {
        return new KeybindSetting(id, title(id), description(id));
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
