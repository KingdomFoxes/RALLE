package org.kingdomfoxes.ralle.lfg.client;

@FunctionalInterface
public interface PartyCommandExecutor {
    PartyCommandExecutor IGNORE = ign -> {};

    void kick(String ign);

    /** Sends one explicitly confirmed host party disband command. */
    default void disband() {}

    /** Sends one explicitly queued, locally validated Wynncraft party invitation. */
    default void invite(String ign) {}
}
