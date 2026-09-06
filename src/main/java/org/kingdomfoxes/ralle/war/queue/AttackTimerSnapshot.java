package org.kingdomfoxes.ralle.war.queue;

import java.util.Objects;

/** Immutable timer data projected from Wynntils; no mutable model object is retained. */
public record AttackTimerSnapshot(String territoryName, long timerEndMillis) {
    public AttackTimerSnapshot {
        territoryName = Objects.requireNonNull(territoryName, "territoryName");
        if (territoryName.isBlank()) throw new IllegalArgumentException("territoryName must not be blank");
    }
}
