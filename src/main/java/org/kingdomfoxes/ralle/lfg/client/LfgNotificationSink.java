package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

@FunctionalInterface
public interface LfgNotificationSink {
    LfgNotificationSink IGNORE = ping -> {};

    void partyPing(LfgProtocol.PartyPingFrame ping);
}
