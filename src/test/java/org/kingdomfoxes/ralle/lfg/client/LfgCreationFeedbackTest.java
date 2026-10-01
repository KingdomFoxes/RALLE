package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class LfgCreationFeedbackTest {
    @Test void cancellationIsSilentEvenThroughFutureWrappers() {
        assertNull(LfgCreationFeedback.failure(new CompletionException(new CancellationException())));
    }

    @Test void classifiesGatewayBeforeItsUnderlyingTransportCause() {
        var transport = new LfgGatewayException("private diagnostic", new java.io.IOException("socket failure"));
        assertEquals(LfgCreationFeedback.key("unavailable"),
                LfgCreationFeedback.failure(new CompletionException(transport)));
    }

    @Test void unknownDiagnosticsBecomeGenericButConflictAndOutageRemainUseful() {
        assertEquals(LfgCreationFeedback.key("failed"), LfgCreationFeedback.failure(new RuntimeException("private diagnostic")));
        assertEquals(LfgCreationFeedback.key("already-exists"), LfgCreationFeedback.failure(error(409, "RAID_ALREADY_LISTED")));
        assertEquals(LfgCreationFeedback.key("unavailable"), LfgCreationFeedback.failure(error(503, "UPSTREAM_UNAVAILABLE")));
        assertEquals(LfgCreationFeedback.key("already-active"), LfgCreationFeedback.failure(error(409, "PLAYER_ALREADY_ACTIVE")));
    }

    @Test void unresolvedOutcomeDoesNotInviteImmediateRetry() {
        assertEquals(LfgCreationFeedback.key("unconfirmed"), LfgCreationFeedback.failure(
                new CompletionException(new LfgMutationOutcomeUnknownException("create", UUID.randomUUID(), "lost context"))));
    }

    @Test void importedPartyRejectionsExplainTheCause() {
        assertEquals(LfgCreationFeedback.key("already-active"),
                LfgCreationFeedback.failure(error(409, "PARTY_MEMBER_ALREADY_ACTIVE")));
        assertEquals(LfgCreationFeedback.key("party-unresolved"),
                LfgCreationFeedback.failure(error(409, "PARTY_IDENTITY_UNRESOLVED")));
        assertEquals(LfgCreationFeedback.key("party-unresolved"),
                LfgCreationFeedback.failure(error(404, "PLAYER_NOT_FOUND")));
        assertEquals(LfgCreationFeedback.key("party-invalid"),
                LfgCreationFeedback.failure(error(409, "INVALID_PARTY")));
    }

    @Test void changedRosterRetainsItsSpecificRecoveryMessage() {
        assertEquals(LfgCreationFeedback.key("party-changed"), LfgCreationFeedback.failure(
                new CompletionException(new LfgCreationFeedback.CreationException("party-changed"))));
    }

    private static LfgGatewayException error(int status, String code) {
        return new LfgGatewayException(status, new LfgProtocol.Error(code, "private diagnostic", false, null, null));
    }
}
