package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.List;
import java.util.UUID;

/** Local presentation boundary for host party-filled invitations and their errors. */
public interface HostPartyInviteSink {
    HostPartyInviteSink IGNORE = new HostPartyInviteSink() {
        @Override public void partyFilled(UUID lobbyId, List<LfgProtocol.Member> targets) {}
        @Override public void error(String message) {}
    };

    void partyFilled(UUID lobbyId, List<LfgProtocol.Member> targets);

    void error(String message);
}
