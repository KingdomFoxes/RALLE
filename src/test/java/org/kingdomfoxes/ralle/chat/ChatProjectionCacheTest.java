package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ChatProjectionCacheTest {
    @Test void wrapsOnlyNewMessagesAndPreservesNewestFirstPartialHistory() {
        var cache = new ChatProjectionCache<String, String>();
        var calls = new AtomicInteger();
        java.util.function.Function<String, List<String>> wrap = message -> {
            calls.incrementAndGet();
            return List.of(message + " top", message + " bottom");
        };
        assertEquals(List.of("b bottom", "b top", "a bottom"), cache.project(List.of("b", "a", "old"), 3, "layout", wrap));
        assertEquals(2, calls.get());
        assertEquals(List.of("c bottom", "c top", "b bottom"), cache.project(List.of("c", "b", "a"), 3, "layout", wrap));
        assertEquals(3, calls.get());
        cache.project(List.of("c", "b"), 3, "new font/width", wrap);
        assertEquals(5, calls.get());
        cache.clear();
        cache.project(List.of("c", "b"), 3, "new font/width", wrap);
        assertEquals(7, calls.get());
    }

    @Test void usesIdentityAndEvictsRemovedMessages() {
        var cache = new ChatProjectionCache<String, String>();
        var calls = new AtomicInteger();
        java.util.function.Function<String, List<String>> wrap = message -> List.of("line" + calls.incrementAndGet());
        var original = new String("same");
        var replacement = new String("same");
        assertEquals(List.of("line1"), cache.project(List.of(original), 1, 1, wrap));
        assertEquals(List.of("line2"), cache.project(List.of(replacement), 1, 1, wrap));
        assertEquals(List.of("line3"), cache.project(List.of(original), 1, 1, wrap));
    }
}
