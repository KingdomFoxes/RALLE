package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class Setting<T> implements SettingsEntry {
    private final String id;
    private final Component title;
    private final Component description;
    private final T defaultValue;
    private T value;
    private Consumer<Setting<?>> changeListener = ignored -> {};

    protected Setting(String id, Component title, Component description, T defaultValue) {
        this.id = IdentifierRules.requireValid(id, "setting id");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public final String id() {
        return id;
    }

    public final Component title() {
        return title;
    }

    public final Component description() {
        return description;
    }

    public final T value() {
        return effectiveValue(value);
    }

    /** Runtime restrictions may mask a preference without overwriting the local saved value. */
    protected T effectiveValue(T storedValue) {
        return storedValue;
    }

    protected final T storedValue() {
        return value;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final void set(T value) {
        var checkedValue = validate(Objects.requireNonNull(value, "value"));
        if (Objects.equals(this.value, checkedValue)) return;

        this.value = checkedValue;
        changeListener.accept(this);
    }

    final void load(String serializedValue) {
        this.value = deserialize(serializedValue);
    }

    final void onChanged(Consumer<Setting<?>> listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    public abstract String serialize();

    protected T validate(T value) {
        return value;
    }

    protected abstract T deserialize(String value);
}
