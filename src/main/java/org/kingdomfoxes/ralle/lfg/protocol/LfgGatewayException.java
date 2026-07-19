package org.kingdomfoxes.ralle.lfg.protocol;

/** Structured REST/WebSocket failure without exposing authentication material. */
public final class LfgGatewayException extends RuntimeException {
    private final int status;
    private final LfgProtocol.Error error;

    public LfgGatewayException(int status, LfgProtocol.Error error) {
        super(error.message());
        this.status = status;
        this.error = error;
    }

    public LfgGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.error = new LfgProtocol.Error("TRANSPORT_FAILURE", message, true, null, null);
    }

    public int status() { return status; }
    public LfgProtocol.Error error() { return error; }
    public boolean transportFailure() { return status == 0; }
}
