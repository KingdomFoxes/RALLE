package org.kingdomfoxes.ralle.chat.identity;

import java.util.Objects;
import java.util.Optional;

/** A bounded, read-only projection of a verified Wynncraft guild-chat envelope. */
public record GuildChatMessage(String displayName, Optional<String> resolvedIgn, String body) {
    public GuildChatMessage {
        displayName = Objects.requireNonNull(displayName, "displayName");
        resolvedIgn = Objects.requireNonNull(resolvedIgn, "resolvedIgn");
        body = Objects.requireNonNull(body, "body");
    }
}
