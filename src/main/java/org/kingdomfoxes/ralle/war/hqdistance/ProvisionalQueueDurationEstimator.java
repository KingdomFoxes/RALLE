package org.kingdomfoxes.ralle.war.hqdistance;

/** Historical, release-blocking provisional model pending current in-game attack-preview validation. */
public final class ProvisionalQueueDurationEstimator implements QueueDurationEstimator {
    @Override
    public int estimateSeconds(int connectionCount) {
        if (connectionCount < 1) throw new IllegalArgumentException("connectionCount must be positive");
        return 60 + 60 * connectionCount;
    }
}
