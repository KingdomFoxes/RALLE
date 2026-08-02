package org.kingdomfoxes.ralle.lfg.client;

import java.util.UUID;

/** Current local inputs that determine whether networking is allowed to exist. */
public interface RaidLfgEnvironment {
    boolean enabled();
    String serverHost();
    UUID playerId();
    String ign();
}
