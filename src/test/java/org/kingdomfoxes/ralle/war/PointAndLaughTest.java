package org.kingdomfoxes.ralle.war;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointAndLaughTest {
    // Verbatim plain component text from 2026-09-21-3.log.gz:3563 (without logger metadata).
    private static final String PROSE = "Nobody logged in for the war.";
    private static final String TRIGGER = "\uDAFF\uDFFC\uE001\uDB00\uDC06 " + PROSE;
    // 2026-09-20-2.log.gz:7982, red continuation line rather than the category icon.
    private static final String LINE_PREFIX = "\uDAFF\uDFFC\uE006\uDAFF\uDFFF\uE002\uDAFF\uDFFE ";

    @Test void continuationLineMatchesButPlayerQuotesAndOtherBodiesDoNot() {
        var feature = new PointAndLaugh();
        var commands = new ArrayList<String>();
        for (var text : new String[]{LINE_PREFIX + "User: " + PROSE,
                LINE_PREFIX + "User: " + LINE_PREFIX + PROSE,
                LINE_PREFIX + "[Rank] User: " + PROSE,
                LINE_PREFIX + "Copied to clipboard: " + PROSE,
                LINE_PREFIX + PROSE + " extra", LINE_PREFIX + PROSE.replace(".", ""),
                LINE_PREFIX + PROSE.replace("Nobody", "nobody")}) {
            feature.observe(text, true, false, "reply", 0, commands::add);
        }
        assertTrue(commands.isEmpty());
        feature.observe("\u00a7c" + LINE_PREFIX + PROSE, true, false, "reply", 0, commands::add);
        assertEquals(java.util.List.of("g reply"), commands);
        // Both presentation forms share the same duplicate limiter.
        feature.observe(TRIGGER, true, false, "reply", 100, commands::add);
        assertEquals(1, commands.size());
        feature.observe(LINE_PREFIX + PROSE, true, false, "again", 1000, commands::add);
        assertEquals(java.util.List.of("g reply", "g again"), commands);
    }

    @Test void onlyExactServerChatOnWynncraftTriggersOneGuildCommand() {
        var feature = new PointAndLaugh();
        var commands = new ArrayList<String>();
        feature.observe(TRIGGER, false, false, "hello", 0, commands::add);
        feature.observe(TRIGGER, true, true, "hello", 0, commands::add);
        feature.observe(TRIGGER, true, false, "", 0, commands::add);
        feature.observe(TRIGGER, true, false, "  ", 0, commands::add);
        for (var text : new String[]{PROSE, "Nobody logged in for the war", "[Guild] User: " + TRIGGER,
                "[Guild] User: " + PROSE, "User: " + PROSE, "User: " + TRIGGER,
                "[Party] User: " + TRIGGER, "[User -> You] " + TRIGGER,
                "\uDAFF\uDFFC\uE002\uDB00\uDC06 " + PROSE,
                "\uE001 " + PROSE, TRIGGER.replace("Nobody", "nobody"),
                TRIGGER.substring(0, TRIGGER.length() - 1), TRIGGER + " extra"}) {
            feature.observe(text, true, false, "hello", 0, commands::add);
        }
        assertTrue(commands.isEmpty());
        feature.observe("§c" + TRIGGER + "§r", true, false, "Hello /p friends!", 0, commands::add);
        assertEquals(java.util.List.of("g Hello /p friends!"), commands);
        feature.observe(TRIGGER, true, false, "hello", 999, commands::add);
        assertEquals(1, commands.size());
        feature.observe(TRIGGER, true, false, "changed", 1000, commands::add);
        assertEquals("g changed", commands.getLast());
        feature.reset();
        feature.observe(TRIGGER, true, false, "next connection", 1001, commands::add);
        assertEquals(3, commands.size());
    }
}
