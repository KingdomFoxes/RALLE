package org.kingdomfoxes.ralle.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.settings.ActionEntry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsSubcategory;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.CustomPanelEntry;

import java.util.List;

public final class RalleSettings {
    public static final String INTERFACE_FONT_ID = "interface-font";
    public static final String INTERNAL_GUILD_RANKS_ID = "internal-guild-ranks";
    public static final String GUILD_RANK_STYLE_ID = "guild-rank-style";
    public static final String CONSUMABLE_HIGHLIGHTS_ENABLED_ID = "consumable-highlights-enabled";
    public static final String CONSUMABLE_HIGHLIGHT_RULES_ID = "consumable-highlight-rules";
    public static final String CONSUMABLE_HIGHLIGHT_PROVIDER_ID = "consumable-highlight-editor";
    public static final String HQ_DISTANCE_ENABLED_ID = "hq-distance-enabled";
    public static final String WAR_QUEUE_ATTRIBUTION_ENABLED_ID = "queue-attribution-enabled";

    public static final String HQ_DISTANCE_KEYBIND_ID = "hq-distance-keybind";
    public static final String QUEUE_SELF_COLOR_ID = "queue-self-color";
    private RalleSettings() {}

    public static void register(SettingsRegistry registry) {
        registry.registerCategory(new SettingsCategory(
                "about",
                Component.translatable("ralle.settings.about"),
                Component.translatable("ralle.settings.about.description"),
                List.of(
                        action("edit-huds"),
                        choice(INTERFACE_FONT_ID, "vanilla", "vanilla", "karla")
                ),
                List.of()
        ));

        registry.registerCategory(new SettingsCategory(
                "chat",
                Component.translatable("ralle.settings.category.chat"),
                Component.translatable("ralle.settings.category.chat.description"),
                List.of(action("edit-chat-layout")),
                List.of(
                        subcategory("appearance",
                                toggle("hide-chat-scrollbar"),
                                toggle("remove-chat-system-indicators"),
                                toggle("chat-timestamps")
                        ),
                        subcategory("input", toggle("chat-type-tabbing")),
                        subcategory("guild-ranks",
                                toggle(INTERNAL_GUILD_RANKS_ID),
                                choice(GUILD_RANK_STYLE_ID, "titles", "titles", "stars", "stars-and-titles")
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
                        subcategory("chat-history",
                                toggle("persistent-chat-enabled"),
                                choice("persistent-chat-limit", "500", "300", "500", "1000", "1500")
                        ),
                        subcategory("screenshots",
                                toggle("chat-screenshot-enabled"),
                                toggle("chat-screenshot-snap-to-text"),
                                toggle("chat-screenshot-smooth-expansion"),
                                toggle("chat-selection-sounds")
                        )
                )
        ));

        registry.registerCategory(new SettingsCategory(
                "raid-lfg",
                Component.translatable("ralle.settings.category.raid-lfg"),
                Component.translatable("ralle.settings.category.raid-lfg.description"),
                List.of(toggle("raid-lfg-enabled"), action("edit-notification-position")),
                List.of(
                        subcategory("notifications",
                                toggle("new-party-notifications"),
                                toggle("reopened-party-notifications"),
                                toggle("party-status-notifications"),
                                toggle("auto-pop-out-main-ui"),
                                toggle("notification-sounds")
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
                                toggle("raid-lfg-create-selector-wheel"),
                                keybind("raid-lfg-kick-keybind"),
                                toggle("raid-lfg-kick-selector-wheel"),
                                keybind("automatic-raid-requeue-keybind")
                        )
                )
        ));

        registry.registerCategory(new SettingsCategory(
                "war",
                Component.translatable("ralle.settings.category.war"),
                Component.translatable("ralle.settings.category.war.description"),
                List.of(),
                List.of(
                        subcategory("attack-timers", toggle(WAR_QUEUE_ATTRIBUTION_ENABLED_ID),
                                new org.kingdomfoxes.ralle.api.settings.ColorSetting(QUEUE_SELF_COLOR_ID,
                                        title(QUEUE_SELF_COLOR_ID), description(QUEUE_SELF_COLOR_ID), 0x5555FF)),
                        subcategory("territory-map", toggle(HQ_DISTANCE_ENABLED_ID),
                                new KeybindSetting(HQ_DISTANCE_KEYBIND_ID, title(HQ_DISTANCE_KEYBIND_ID),
                                        description(HQ_DISTANCE_KEYBIND_ID), "key.keyboard.left.control")),
                        subcategory("consumables",
                                toggle(CONSUMABLE_HIGHLIGHTS_ENABLED_ID),
                                customPanel(CONSUMABLE_HIGHLIGHT_RULES_ID, CONSUMABLE_HIGHLIGHT_PROVIDER_ID)
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
        registry.requireEnabled(QUEUE_SELF_COLOR_ID, WAR_QUEUE_ATTRIBUTION_ENABLED_ID);
        registry.requireEnabled(HQ_DISTANCE_KEYBIND_ID, HQ_DISTANCE_ENABLED_ID);
        registry.requireEnabled(CONSUMABLE_HIGHLIGHT_RULES_ID, CONSUMABLE_HIGHLIGHTS_ENABLED_ID);
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
        registry.requireEnabled("raid-lfg-create-selector-wheel", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-kick-keybind", "raid-lfg-enabled");
        registry.requireEnabled("raid-lfg-kick-selector-wheel", "raid-lfg-enabled");
        registry.requireEnabled("automatic-raid-requeue-keybind", "raid-lfg-enabled");
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

    private static CustomPanelEntry customPanel(String id, String providerId) {
        return new CustomPanelEntry(id, title(id), description(id), providerId);
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
        if ("text-shadow".equals(id)) {
            return Component.translatable(
                    "ralle.settings.option.text-shadow.description",
                    Component.translatable("ralle.settings.option.text-shadow.description.note")
            );
        }
        return Component.translatable("ralle.settings.option." + id + ".description");
    }
}
