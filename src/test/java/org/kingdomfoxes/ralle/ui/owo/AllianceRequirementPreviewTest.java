package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceRequirementPreviewTest {
    @Test
    void previewFlagIsOffUnlessExplicitlyTrue() {
        assertFalse(AllianceRequirementPreview.enabled(null));
        assertFalse(AllianceRequirementPreview.enabled("false"));
        assertFalse(AllianceRequirementPreview.enabled("yes"));
        assertTrue(AllianceRequirementPreview.enabled("true"));
        assertTrue(AllianceRequirementPreview.enabled("TRUE"));
    }

    @Test
    void absentSystemPropertyKeepsPreviewDisabled() {
        var previous = System.getProperty(AllianceRequirementPreview.ENABLE_PROPERTY);
        try {
            System.clearProperty(AllianceRequirementPreview.ENABLE_PROPERTY);
            assertFalse(AllianceRequirementPreview.enabled());
            System.setProperty(AllianceRequirementPreview.ENABLE_PROPERTY, "true");
            assertTrue(AllianceRequirementPreview.enabled());
        } finally {
            if (previous == null) System.clearProperty(AllianceRequirementPreview.ENABLE_PROPERTY);
            else System.setProperty(AllianceRequirementPreview.ENABLE_PROPERTY, previous);
        }
    }

    @Test
    void requestAdvancesThroughEveryStateWithoutSkippingOrDuplicatingTransitions() {
        var now = new long[]{1_000_000_000L};
        var preview = new AllianceRequirementPreview(
                AllianceRequirementPreview.defaultGuilds(), () -> now[0]);

        assertEquals(AllianceRequirementPreview.State.REQUESTABLE, state(preview, "seq"));
        assertFalse(preview.simulateAcceptance("seq"));
        assertTrue(preview.request("seq"));
        assertFalse(preview.request("seq"));
        assertEquals(AllianceRequirementPreview.State.SENDING, state(preview, "seq"));

        now[0] += AllianceRequirementPreview.SENDING_DURATION_NANOS - 1;
        assertFalse(preview.tick());
        assertEquals(AllianceRequirementPreview.State.SENDING, state(preview, "seq"));

        now[0]++;
        assertTrue(preview.tick());
        assertFalse(preview.tick());
        assertEquals(AllianceRequirementPreview.State.WAITING, state(preview, "seq"));
        assertFalse(preview.request("seq"));

        assertTrue(preview.simulateAcceptance("seq"));
        assertFalse(preview.simulateAcceptance("seq"));
        assertEquals(AllianceRequirementPreview.State.ALLIED, state(preview, "seq"));
        assertTrue(preview.readyToJoin());
    }

    @Test
    void multipleGuildsRemainOrderedAndAllMustAcceptBeforeReady() {
        var now = new long[]{0L};
        var longName = "Sequenced Example Guild With A Deliberately Long Display Name";
        var preview = new AllianceRequirementPreview(List.of(
                new AllianceRequirementPreview.Guild("seq", longName, "SEQ"),
                new AllianceRequirementPreview.Guild("fox", "Kingdom of Foxes", "FOX")
        ), () -> now[0]);

        assertEquals(List.of("seq", "fox"), preview.rows().stream()
                .map(row -> row.guild().id()).toList());
        assertEquals(longName, preview.rows().getFirst().guild().name());

        assertTrue(preview.request("seq"));
        assertTrue(preview.request("fox"));
        now[0] = AllianceRequirementPreview.SENDING_DURATION_NANOS;
        assertTrue(preview.tick());
        assertTrue(preview.simulateAcceptance("seq"));
        assertFalse(preview.readyToJoin());
        assertTrue(preview.simulateAcceptance("fox"));
        assertTrue(preview.readyToJoin());
    }

    @Test
    void duplicateGuildIdsAreRejected() {
        var duplicate = new AllianceRequirementPreview.Guild("seq", "Another SEQ", "SEQ");
        assertThrows(IllegalArgumentException.class, () -> new AllianceRequirementPreview(
                List.of(duplicate, duplicate), () -> 0L));
    }

    @Test
    void loadingIndicatorAnimatesOnlyWhileSending() {
        var now = new long[]{0L};
        var preview = new AllianceRequirementPreview(
                AllianceRequirementPreview.defaultGuilds(), () -> now[0]);

        assertEquals("", preview.loadingIndicator("seq"));
        preview.request("seq");
        assertEquals("·", preview.loadingIndicator("seq"));
        now[0] = 160_000_000L;
        assertEquals("··", preview.loadingIndicator("seq"));
        now[0] = 320_000_000L;
        assertEquals("···", preview.loadingIndicator("seq"));
    }

    private static AllianceRequirementPreview.State state(AllianceRequirementPreview preview, String guildId) {
        return preview.rows().stream()
                .filter(row -> row.guild().id().equals(guildId))
                .findFirst()
                .orElseThrow()
                .state();
    }
}
