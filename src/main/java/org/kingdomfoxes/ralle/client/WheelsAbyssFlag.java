package org.kingdomfoxes.ralle.client;

import com.google.gson.JsonParser;
import org.kingdomfoxes.ralle.settings.LocalStateSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/** Persists the one-time Wheels abyss flag in local state ("laterthatday": 1 -> 0 once triggered). */
public final class WheelsAbyssFlag {
    public static final String SECTION_NAME = "laterthatday";
    private static final Logger LOGGER = LoggerFactory.getLogger(WheelsAbyssFlag.class);

    private final LocalStateSection section;
    private int value;

    public WheelsAbyssFlag(LocalStateSection section) {
        this.section = Objects.requireNonNull(section, "section");
        this.value = load();
        if (!section.exists()) save();
    }

    public synchronized boolean enabled() {
        return value == 1;
    }

    public synchronized int value() {
        return value;
    }

    /** Marks the easter egg as triggered, persisting 0 to local state. Returns true only on the 1 -> 0 transition. */
    public synchronized boolean consume() {
        if (value != 1) return false;
        value = 0;
        save();
        return true;
    }

    private int load() {
        if (!section.exists()) return 1;
        try {
            String raw = section.read();
            if (raw == null) return 1;
            var element = JsonParser.parseString(raw);
            if (element.isJsonPrimitive()) {
                var primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    int n = primitive.getAsInt();
                    if (n == 0 || n == 1) return n;
                } else if (primitive.isString()) {
                    String s = primitive.getAsString().strip();
                    if ("0".equals(s)) return 0;
                    if ("1".equals(s)) return 1;
                }
            }
            LOGGER.warn("Invalid RALLE {} state in {}; disabling", SECTION_NAME, section);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not read RALLE {} state from {}; disabling", SECTION_NAME, section, failure);
        }
        return 0;
    }

    private void save() {
        try {
            section.write(Integer.toString(value));
        } catch (IOException failure) {
            LOGGER.error("Could not save RALLE {} state to {}", SECTION_NAME, section, failure);
        }
    }
}
