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
    public static final String INTERNAL_GUILD_RANKS_ID = "internal-guild-ranks";

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
                                toggle("remove-chat-system-indicators"),
                                toggle("chat-timestamps")
                        ),
                        subcategory("guild-ranks", toggle(INTERNAL_GUILD_RANKS_ID)),
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
                        subcategory("chat-history",
                                toggle("persistent-chat-enabled"),
                                choice("persistent-chat-limit", "500", "300", "500", "1000", "1500")
                        ),
                        subcategory("screenshots",
                                toggle("chat-screenshot-enabled", true),
                                toggle("chat-screenshot-snap-to-text"),
                                toggle("chat-screenshot-smooth-expansion"),
                                toggle("chat-selection-sounds", true)
                        )
                )
        ));

        registry.registerCategory(new SettingsCategory(
                "raid-lfg",
                Component.translatable("ralle.settings.category.raid-lfg"),
                Component.translatable("ralle.settings.category.raid-lfg.description"),
                List.of(
                        subcategory("general", toggle("raid-lfg-enabled", true)),
                        subcategory("notifications",
                                toggle("new-party-notifications", true),
                                toggle("reopened-party-notifications", true),
                                toggle("party-status-notifications", true),
                                toggle("auto-pop-out-main-ui", true),
                                toggle("notification-sounds", true),
                                action("edit-notification-position")
                        ),
                        subcategory("controls",
                                keybind("raid-lfg-keybind", "key.keyboard.f1"),
                                keybind("raid-lfg-join-keybind", "key.keyboard.f2"),
                                keybind("raid-lfg-close-keybind", "key.keyboard.f3"),
                                keybind("raid-lfg-leave-disband-keybind", "key.keyboard.f4"),
                                keybind("raid-lfg-party-filled-keybind", "key.keyboard.f5"),
                                keybind("raid-lfg-ping-keybind", "key.keyboard.f6"),
                                keybind("raid-lfg-lock-keybind", "key.keyboard.f7"),
                                keybind("raid-lfg-create-keybind", "key.keyboard.f8"),
                                keybind("raid-lfg-kick-keybind", "key.keyboard.f9")
                        )
                )
        ));

        registry.requireEnabled("message-direction", "message-direction-enabled");
        registry.requireEnabled("horizontal-alignment", "horizontal-alignment-enabled");
        registry.requireEnabled("text-shadow", "text-shadow-enabled");
        registry.requireEnabled("persistent-chat-limit", "persistent-chat-enabled");
        registry.requireEnabled("chat-screenshot-snap-to-text", "chat-screenshot-enabled");
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

    private static BooleanSetting toggle(String id, boolean defaultValue) {
        return new BooleanSetting(id, title(id), description(id), defaultValue);
    }

    private static ActionEntry action(String id) {
        return new ActionEntry(id, title(id), description(id));
    }

    private static KeybindSetting keybind(String id, String defaultValue) {
        return new KeybindSetting(id, title(id), description(id), defaultValue);
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
