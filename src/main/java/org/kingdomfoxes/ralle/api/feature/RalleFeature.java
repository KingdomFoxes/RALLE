package org.kingdomfoxes.ralle.api.feature;

/** A self-contained, opt-in client feature registered during bootstrap. */
public interface RalleFeature {
    String id();

    void initialize();
}
