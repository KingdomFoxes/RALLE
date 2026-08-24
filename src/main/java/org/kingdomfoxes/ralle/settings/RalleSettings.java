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
                List.of(subcategory("interface",
                        action("edit-huds"),
                        choice(INTERFACE_FONT_ID, "vanilla", "vanilla", "karla")
                ))
        ));

        registry.registerCategory(new SettingsCategory(
                "chat",
                Component.translatable("ralle.settings.category.chat"),
                Component.translatable("ralle.settings.category.chat.description"),
                List.of(
                        subcategory("general", action("edit-chat-layout")),
                        subcategory("appearance",
                                toggle("hide-chat-scrollbar"),
                                toggle("remove-chat-system-indicators")
                        ),
                        subcategory("message-direction",
                                toggle("message-direction-enabled"),
                                choice("message-direction", "bottom-up", "bottom-up", "top-down")
                        ),
                        subcategory("horizontal-alignment",
                                toggle("horizontal-alignment-enabled"),
                                choice("horizontal-alignment", "left", "left", "right")
                        ),
                        subcategory("text-shadow",
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
                                toggle("auto-pop-out-main-ui"),
                                toggle("notification-sounds"),
                                action("edit-notification-position")
                        ),
                        subcategory("controls",
                                keybind("raid-lfg-keybind"),
                                keybind("raid-lfg-join-keybind"),
                                keybind("raid-lfg-close-keybind"),
                                keybind("raid-lfg-leave-disband-keybind"),
                                keybind("raid-lfg-party-filled-keybind"),
                                keybind("raid-lfg-ping-keybind"),
                                keybind("raid-lfg-lock-keybind"),
                                keybind("raid-lfg-create-keybind"),
                                keybind("raid-lfg-kick-keybind")
                        )
                )
        ));

        registry.requireEnabled("message-direction", "message-direction-enabled");
        registry.requireEnabled("horizontal-alignment", "horizontal-alignment-enabled");
        registry.requireEnabled("text-shadow", "text-shadow-enabled");
        registry.requireEnabled("chat-screenshot-smooth-expansion", "chat-screenshot-enabled");
        registry.requireEnabled("chat-selection-sounds", "chat-screenshot-enabled");
        registry.requireEnabled("new-party-notifications", "raid-lfg-enabled");
        registry.requireEnabled("reopened-party-notifications", "raid-lfg-enabled");
        registry.requireEnabled("party-status-notifications", "raid-lfg-enabled");
        registry.requireEnabled("auto-pop-out-main-ui", "raid-lfg-enabled");
        registry.requireEnabled("notification-sounds", "raid-lfg-enabled");
        registry.requireEnabled("edit-notification-position", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-join-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-close-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-leave-disband-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-party-filled-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-ping-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-lock-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-create-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-kick-keybind", "raid-lfg-enabled");
    }

    private static SettingsSubcategory subcategory(String id, org.kingdomfoxes.ralle.api.settings.SettingsEntry... entries) {
        return new SettingsSubcategory(
                id,
                Component.translatable("ralle.settings.subcategory." + id),
                Component.translatable("ralle.settings.subcategory." + id + ".description"),
                List.of(entries)
        );
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
