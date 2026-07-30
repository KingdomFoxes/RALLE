package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgActionBarStateTest {
    @Test
    void heldChainStaysVisibleThenLingersAndFadesTogether() {
        long[] now = {10_000};
        var state = new LfgActionBarState(() -> now[0]);
        state.holdRaid("Create +", LfgProtocol.RaidType.TWP, "TWP",
                LfgActionBarState.Tone.ACCENT);

        var held = state.snapshot();
        assertTrue(held.visible());
        assertEquals("Create +", held.prefix());
        assertEquals(LfgProtocol.RaidType.TWP, held.raid());
        assertEquals("TWP", held.raidLabel());
        assertEquals(1d, held.opacity());

        state.release();
        now[0] += LfgActionBarState.LINGER_MILLIS;
        assertEquals(1d, state.snapshot().opacity());
        now[0] += LfgActionBarState.FADE_MILLIS / 2;
        assertEquals(0.5d, state.snapshot().opacity());
        now[0] += LfgActionBarState.FADE_MILLIS / 2;
        assertFalse(state.snapshot().visible());
    }

    @Test
    void plainActionsNeverCarryAStaleRaidItem() {
        var state = new LfgActionBarState(() -> 0);
        state.holdRaid("Create +", LfgProtocol.RaidType.TNA, "TNA",
                LfgActionBarState.Tone.ACCENT);
        state.show("Ping", LfgActionBarState.Tone.POSITIVE);

        assertEquals("Ping", state.snapshot().text());
        assertEquals(null, state.snapshot().raid());
    }

    @Test
    void actionGlyphIsStoredSeparatelyFromInterfaceText() {
        var state = new LfgActionBarState(() -> 0);

        state.show("Ping", LfgActionBarState.Tone.NORMAL, LfgActionGlyph.PING);

        assertEquals("Ping", state.snapshot().text());
        assertEquals(LfgActionGlyph.PING, state.snapshot().glyph());
    }
}
