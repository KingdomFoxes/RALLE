package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.Optional;

/** Replaceable current-region detector; v1 deliberately falls back to EU when unavailable. */
@FunctionalInterface
public interface RaidRegionDetector {
    RaidRegionDetector UNAVAILABLE = Optional::empty;

    Optional<LfgProtocol.Region> detect();
}
