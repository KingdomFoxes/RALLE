package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

/** A locally persisted Minecraft input translation key, or {@value #UNBOUND}. */
public final class KeybindSetting extends Setting<String> {
    public static final String UNBOUND = "unbound";

    public KeybindSetting(String id, Component title, Component description) {
        super(id, title, description, UNBOUND);
    }

    @Override
    public String serialize() { return value(); }

    @Override
    protected String validate(String value) {
        if (value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Keybind values must be non-blank translation keys");
        }
        return value;
    }

    @Override
    protected String deserialize(String value) { return validate(value); }
}
