package org.kingdomfoxes.ralle.lfg.protocol;

/** Indicates a malformed or incompatible Fox protocol payload. */
public final class LfgProtocolException extends RuntimeException {
    public LfgProtocolException(String message) {
        super(message);
    }

    public LfgProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
