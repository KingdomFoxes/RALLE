package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

/** A locally persisted, single-line text value; empty by default. */
public final class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String id, Component title, Component description, int maxLength) {
        super(id, title, description, "");
        this.maxLength = maxLength;
    }

    public int maxLength() { return maxLength; }

    @Override public String serialize() { return value(); }

    @Override protected String validate(String value) {
        if (value.length() > maxLength || value.chars().anyMatch(c -> c < 32 || c == 127 || c == 0xA7)) {
            throw new IllegalArgumentException("Invalid single-line text setting");
        }
        return value;
    }

    @Override protected String deserialize(String value) { return validate(value); }
}
