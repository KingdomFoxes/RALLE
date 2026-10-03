package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

public final class BooleanSetting extends Setting<Boolean> {
    private java.util.function.BooleanSupplier access = () -> true;

    public BooleanSetting(String id, Component title, Component description) {
        this(id, title, description, false);
    }

    public BooleanSetting(String id, Component title, Component description, boolean defaultValue) {
        super(id, title, description, defaultValue);
    }

    @Override
    public String serialize() {
        return Boolean.toString(storedValue());
    }

    void requireAccess(java.util.function.BooleanSupplier access) {
        this.access = java.util.Objects.requireNonNull(access, "access");
    }

    @Override
    protected Boolean effectiveValue(Boolean storedValue) {
        return storedValue && access.getAsBoolean();
    }

    @Override
    protected Boolean deserialize(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false");
    }
}
