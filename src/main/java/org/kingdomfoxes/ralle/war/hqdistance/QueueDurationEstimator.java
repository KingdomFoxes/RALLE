package org.kingdomfoxes.ralle.war.hqdistance;

public interface QueueDurationEstimator {
    int estimateSeconds(int connectionCount);
}
