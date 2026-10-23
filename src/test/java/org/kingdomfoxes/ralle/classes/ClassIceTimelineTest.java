package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassIceTimelineTest {
    @Test void bookClosePreservesIncomingSpeedWithoutALaunchPause() {
        for (long delay : new long[] {100, 800, 7000, 13000}) {
            var timeline = new ClassIceTimeline();
            timeline.start(0);
            double before = timeline.progress(delay) - timeline.progress(delay - 1);
            timeline.bookClosed(delay);
            double after = timeline.progress(delay + 1) - timeline.progress(delay);
            assertEquals(before, after, before * .01);
        }
    }
    @Test void slowOrFastBookArrivalIsContinuousBoundedAndFinishesBeforeRelease() {
        for (long delay : new long[] {0, 100, 800, 7000, 13000}) {
            var timeline = new ClassIceTimeline();
            timeline.start(100);
            double before = timeline.progress(100 + delay);
            assertTrue(before >= 0 && before <= .16);
            timeline.bookClosed(100 + delay);
            assertEquals(before, timeline.progress(100 + delay), 1e-12);
            double previous = before, previousStep = 0;
            for (long elapsed = 10; elapsed <= 1500; elapsed += 10) {
                double next = timeline.progress(100 + delay + elapsed);
                assertTrue(next >= previous && next <= 1);
                assertTrue(next - previous >= previousStep - 1e-12, "exponential-in acceleration");
                previousStep = next - previous; previous = next;
            }
            assertEquals(1, timeline.progress(100 + delay + 1500), 1e-12);
            assertEquals(1, timeline.progress(100 + delay + 1599), 1e-12);
        }
    }
    @Test void duplicateClosesDoNotRestartAndNextSwitchStartsFresh() {
        var timeline = new ClassIceTimeline();
        timeline.start(0); timeline.bookClosed(800);
        double middle = timeline.progress(1800);
        timeline.bookClosed(1800);
        assertEquals(middle, timeline.progress(1800));
        assertEquals(1, timeline.progress(2300), 1e-12);
        timeline.start(9000);
        assertEquals(0, timeline.progress(9000));
        assertTrue(timeline.progress(9500) < .16);
    }
}
