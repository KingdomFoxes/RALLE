package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatBehaviorServiceTest {
    @Test
    void legacyFullValueRemainsThePartialFullShadow() {
        assertEquals(
                ChatBehaviorService.TextShadow.PARTIAL_FULL,
                ChatBehaviorService.parseTextShadow("full")
        );
    }

    @Test
    void wrappedFullValueSelectsTheNewFullShadow() {
        assertEquals(
                ChatBehaviorService.TextShadow.FULL,
                ChatBehaviorService.parseTextShadow("wrapped-full")
        );
    }
}
