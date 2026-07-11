package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

public final class ChoiceSetting extends Setting<String> {
    private final List<String> choices;

    public ChoiceSetting(String id, Component title, Component description, String defaultValue, List<String> choices) {
        super(id, title, description, defaultValue);
        this.choices = List.copyOf(choices);
        if (this.choices.isEmpty()) throw new IllegalArgumentException("A choice setting needs at least one choice");
        if (!this.choices.contains(defaultValue)) throw new IllegalArgumentException("Default value must be a choice");
        if (this.choices.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Choices cannot contain null");
    }

    public List<String> choices() {
        return choices;
    }

    public String nextValue() {
        return choices.get((choices.indexOf(value()) + 1) % choices.size());
    }

    @Override
    public String serialize() {
        return value();
    }

    @Override
    protected String validate(String value) {
        if (!choices.contains(value)) throw new IllegalArgumentException("Unknown choice: " + value);
        return value;
    }

    @Override
    protected String deserialize(String value) {
        return validate(value);
    }
}
