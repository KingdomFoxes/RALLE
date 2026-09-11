package org.kingdomfoxes.ralle.lfg.client;

import com.wynntils.core.components.Models;

/** Loaded only with a supported Wynntils installation; never issues party commands. */
final class WynntilsPartySnapshotSource {
    private WynntilsPartySnapshotSource() {}

    static WynncraftPartySnapshot current() {
        if (!Models.Party.isInParty()) return new WynncraftPartySnapshot(null, java.util.List.of());
        // PartyModel extracts real usernames from nickname hover metadata in party events.
        return new WynncraftPartySnapshot(Models.Party.getPartyLeader().orElse(null),
                Models.Party.getPartyMembers());
    }
}
