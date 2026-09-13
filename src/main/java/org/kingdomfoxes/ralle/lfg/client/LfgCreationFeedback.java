package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Shared, localizable creation feedback. Never render arbitrary exception or backend text. */
public final class LfgCreationFeedback {
    private LfgCreationFeedback() {}

    public static String key(String state) { return "ralle.lfg.create." + state; }

    public static String progress(RaidLfgService service) {
        return key(service.outcomeUnknown(null, "create") ? "checking" : "processing");
    }

    /** Null means creation is available. Pending takes precedence during reconciliation. */
    public static String unavailable(RaidLfgService service) {
        if (service.pendingCreate()) return progress(service);
        return switch (service.lifecycle()) {
            case ONLINE -> {
                var capabilities = service.store().state().capabilities();
                yield capabilities != null && capabilities.create() ? null
                        : capabilities != null && "PLAYER_ALREADY_ACTIVE".equals(capabilities.disabledReasons().get("create"))
                        ? key("already-active") : key("not-allowed");
            }
            case INELIGIBLE -> key("ineligible");
            case OUTDATED -> key("outdated");
            case DISABLED -> key("disabled");
            case NOT_ON_WYNNCRAFT -> key("wynncraft-only");
            default -> key("unavailable");
        };
    }

    /** Cancellation is deliberately silent. Unknown outcomes must not invite immediate resubmission. */
    public static String failure(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof CancellationException) return null;
        if (failure instanceof CreationException local) return key(local.state);
        if (failure instanceof LfgMutationOutcomeUnknownException) return key("unconfirmed");
        if (failure instanceof LfgGatewayException gateway) {
            return switch (gateway.error().code()) {
                case "RAID_ALREADY_LISTED" -> key("already-exists");
                case "WYNNCRAFT_UNAVAILABLE", "UPSTREAM_UNAVAILABLE", "TRANSPORT_FAILURE" -> key("unavailable");
                case "INELIGIBLE", "INELIGIBLE_GUILD" -> key("ineligible");
                case "UNSUPPORTED_PROTOCOL" -> key("outdated");
                case "PLAYER_ALREADY_ACTIVE" -> key("already-active");
                case "RATE_LIMITED" -> key("rate-limited");
                default -> gateway.status() >= 500 ? key("unavailable") : key("failed");
            };
        }
        return key("failed");
    }

    public static final class CreationException extends IllegalStateException {
        private final String state;
        public CreationException(String state) { super(state); this.state = state; }
    }
}
