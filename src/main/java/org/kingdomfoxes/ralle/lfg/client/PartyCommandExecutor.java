package org.kingdomfoxes.ralle.lfg.client;

@FunctionalInterface
public interface PartyCommandExecutor {
    PartyCommandExecutor IGNORE = ign -> {};

    void kick(String ign);
}
