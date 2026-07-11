package org.kingdomfoxes.ralle.lfg.client;

import java.util.List;

/**
 * Presentation-only lobby data used while the LFG service is not implemented.
 */
public record FakeRaidLobby(
        String raid,
        Region region,
        Status status,
        boolean locked,
        String note,
        List<Member> members
) {
    public enum Region { NA, EU, AS }

    public enum Status { OPEN, IN_RAID }

    public record Member(String uuid, String ign, String guild, boolean host) {}
}
