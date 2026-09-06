package org.kingdomfoxes.ralle.war.hqdistance;

import java.util.OptionalInt;

public record HqInspection(String upperLabel, String lowerLabel, OptionalInt connectionCount, int upperColor) {
    public HqInspection(String upperLabel, String lowerLabel, OptionalInt connectionCount) {
        this(upperLabel, lowerLabel, connectionCount, 0xFFFFFFFF);
    }

    public boolean hasLowerLabel() {
        return !lowerLabel.isEmpty();
    }
}
