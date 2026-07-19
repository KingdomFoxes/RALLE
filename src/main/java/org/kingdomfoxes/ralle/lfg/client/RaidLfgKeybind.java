package org.kingdomfoxes.ralle.lfg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.ui.owo.RaidLfgScreen;

/** Keeps the persisted, initially-unbound LFG shortcut synchronized with Minecraft input. */
public final class RaidLfgKeybind {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("ralle", "controls")
    );
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final KeybindSetting setting;
    private final KeyMapping mapping;
    private final RaidLfgService service;
    private String appliedValue;

    public RaidLfgKeybind(Minecraft minecraft, SettingsRegistry settings, RaidLfgService service) {
        this.minecraft = minecraft;
        this.service = service;
        this.enabled = settings.setting("raid-lfg-enabled", BooleanSetting.class);
        this.setting = settings.setting("raid-lfg-keybind", KeybindSetting.class);
        this.mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ralle.raid-lfg",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));
        applySetting();
    }

    public void tick() {
        if (!setting.value().equals(appliedValue)) applySetting();
        while (mapping.consumeClick()) {
            if (enabled.value()) minecraft.setScreen(new RaidLfgScreen(minecraft.screen, service));
        }
    }

    private void applySetting() {
        InputConstants.Key key;
        try {
            key = KeybindSetting.UNBOUND.equals(setting.value())
                    ? InputConstants.UNKNOWN : InputConstants.getKey(setting.value());
        } catch (IllegalArgumentException ignored) {
            key = InputConstants.UNKNOWN;
        }
        mapping.setKey(key);
        KeyMapping.resetMapping();
        appliedValue = setting.value();
    }
}
