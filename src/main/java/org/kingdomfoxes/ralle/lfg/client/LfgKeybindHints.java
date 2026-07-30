package org.kingdomfoxes.ralle.lfg.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.List;
import java.util.Optional;

/** Live key-label projection for notification-card action hints. */
public final class LfgKeybindHints {
    private static final List<String> ACTION_IDS = List.of(
            RaidLfgKeybinds.OPEN_ID,
            RaidLfgKeybinds.JOIN_ID,
            RaidLfgKeybinds.CLOSE_ID,
            RaidLfgKeybinds.LEAVE_DISBAND_ID,
            RaidLfgKeybinds.PING_ID,
            RaidLfgKeybinds.LOCK_ID,
            RaidLfgKeybinds.CREATE_ID,
            RaidLfgKeybinds.KICK_ID
    );

    private final SettingsRegistry settings;

    public LfgKeybindHints(SettingsRegistry settings) {
        this.settings = settings;
    }

    public String joinLabel(String action) {
        return label(action, RaidLfgKeybinds.JOIN_ID);
    }

    public String leaveDisbandLabel(String action) {
        return label(action, RaidLfgKeybinds.LEAVE_DISBAND_ID);
    }

    public Optional<String> closeKeyName() {
        return keyName(RaidLfgKeybinds.CLOSE_ID);
    }

    private String label(String action, String settingId) {
        return keyName(settingId).map(key -> action + " [" + key + "]").orElse(action);
    }

    Optional<String> keyName(String settingId) {
        var setting = settings.setting(settingId, KeybindSetting.class);
        String value = setting.value();
        if (KeybindSetting.UNBOUND.equals(value)) return Optional.empty();
        long matches = ACTION_IDS.stream()
                .map(id -> settings.setting(id, KeybindSetting.class).value())
                .filter(value::equals)
                .count();
        if (matches > 1) return Optional.empty();
        try {
            return Optional.of(InputConstants.getKey(value).getDisplayName().getString());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
