package org.kingdomfoxes.ralle.lfg.client;

/** Physical release guard and shared mode routing for modifier tick recovery. */
final class LfgModifierInput {
    enum Route { NONE, CREATE_WHEEL, KICK_WHEEL, CREATE_CHORD, KICK_CHORD }
    private boolean awaitingRelease;

    void closed(boolean createDown, boolean kickDown) { awaitingRelease = createDown || kickDown; }
    void observe(boolean createDown, boolean kickDown) {
        if (!createDown && !kickDown) awaitingRelease = false;
    }
    boolean awaitingRelease() { return awaitingRelease; }
    Route route(boolean createDown, boolean kickDown, boolean createWheel, boolean kickWheel) {
        if (awaitingRelease || createDown == kickDown) return Route.NONE;
        return createDown ? (createWheel ? Route.CREATE_WHEEL : Route.CREATE_CHORD)
                : (kickWheel ? Route.KICK_WHEEL : Route.KICK_CHORD);
    }
}
