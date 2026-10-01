package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LfgElapsedTimerComponentTest {
    @Test
    void measuresOnlyWhenDisplayedSecondOrTypographyChanges() {
        var measures = new AtomicInteger();
        var cache = new LfgElapsedTimerComponent.TextCache(text -> {
            measures.incrementAndGet();
            return text.getString().length() * 6;
        });
        var font = new Object();
        var language = new Object();
        var first = cache.resolve(59, false, font, language, 0);
        for (int frame = 0; frame < 240; frame++) {
            assertSame(first, cache.resolve(59, false, font, language, 0));
        }
        assertEquals(1, measures.get());
        assertEquals("0:59", first.text().getString());
        assertEquals(24, first.width());
        var minute = cache.resolve(60, false, font, language, 0);
        assertEquals("1:00", minute.text().getString());
        var changedFont = cache.resolve(60, true, font, language, 0);
        assertNotSame(minute, changedFont);
        var reloaded = cache.resolve(60, true, font, language, 1);
        assertNotSame(changedFont, reloaded);
        language = new Object();
        var translated = cache.resolve(60, true, font, language, 1);
        assertNotSame(reloaded, translated);
        assertNotSame(translated, cache.resolve(60, true, new Object(), language, 1));
        assertEquals(6, measures.get());
        assertEquals("1:00:00", cache.resolve(3600, false, font, language, 1).text().getString());
    }

    @Test
    void fractionalCreationTimeDoesNotAdvanceTimerEarly() {
        var created = Instant.parse("2026-09-21T12:00:00.750Z");
        assertEquals(0, LfgElapsedTimerComponent.elapsedSeconds(created, created.plusMillis(999)));
        assertEquals(1, LfgElapsedTimerComponent.elapsedSeconds(created, created.plusMillis(1000)));
        assertEquals(0, LfgElapsedTimerComponent.elapsedSeconds(created, created.minusNanos(1)));
        assertEquals(0, LfgElapsedTimerComponent.elapsedSeconds(null, created));
    }

    @Test
    void frozenTimeoutReusesPresentationAndClockRollbackRebuildsIt() {
        var cache = new LfgElapsedTimerComponent.TextCache(text -> text.getString().length());
        var font = new Object();
        var language = new Object();
        var timeout = cache.resolve(1800, false, font, language, 0);
        assertEquals("30:00", timeout.text().getString());
        assertSame(timeout, cache.resolve(1800, false, font, language, 0));
        assertEquals("0:00", cache.resolve(0, false, font, language, 0).text().getString());
    }
}
