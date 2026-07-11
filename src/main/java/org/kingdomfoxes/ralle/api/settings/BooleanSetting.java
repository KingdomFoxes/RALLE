package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String id, Component title, Component description) {
        super(id, title, description, false);
    }

    @Override
    public String serialize() {
        return Boolean.toString(value());
    }

    @Override
    protected Boolean deserialize(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false");
    }
}
