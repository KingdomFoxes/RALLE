package org.kingdomfoxes.ralle.lfg.client;

import java.util.UUID;

/** A submitted mutation whose final backend outcome cannot be safely inferred after resynchronization. */
public final class LfgMutationOutcomeUnknownException extends RuntimeException {
    private final String action;
    private final UUID idempotencyKey;

    LfgMutationOutcomeUnknownException(String action, UUID idempotencyKey, String message) {
        super(message);
        this.action = action;
        this.idempotencyKey = idempotencyKey;
    }

    public String action() {
        return action;
    }

    public UUID idempotencyKey() {
        return idempotencyKey;
    }
}
