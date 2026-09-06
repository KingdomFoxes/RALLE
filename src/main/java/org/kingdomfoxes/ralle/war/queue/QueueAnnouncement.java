package org.kingdomfoxes.ralle.war.queue;

import java.util.Objects;
import java.util.regex.Pattern;

public record QueueAnnouncement(String territoryName, String senderIgn) {
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public QueueAnnouncement {
        territoryName = Objects.requireNonNull(territoryName, "territoryName");
        senderIgn = Objects.requireNonNull(senderIgn, "senderIgn");
        if (territoryName.isBlank()) throw new IllegalArgumentException("territoryName must not be blank");
        if (!IGN.matcher(senderIgn).matches()) throw new IllegalArgumentException("Invalid Minecraft username");
    }
}
