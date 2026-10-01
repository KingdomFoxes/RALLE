package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsernameSpanTest {
    @Test
    void findsOnlyCanonicalUsernameInsideFullServerLabel() {
        var full = Component.empty()
                .append(Component.literal("[Fox] ").withColor(0x55ffff))
                .append(Component.literal("ExamplePlayer").withColor(0xffcc44))
                .append(Component.literal(" [Lv. 100]"));
        var span = UsernameSpan.find(full, "ExamplePlayer").orElseThrow();
        assertEquals("[Fox] ", span.prefix().getString());
        assertEquals("ExamplePlayer", span.username().getString());
        assertTrue(UsernameSpan.find(full, "OtherPlayer").isEmpty());
    }
}
