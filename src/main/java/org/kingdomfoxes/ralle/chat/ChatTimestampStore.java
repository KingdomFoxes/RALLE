package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Session-only receive times keyed by the identity of each logical Minecraft chat message. */
public final class ChatTimestampStore {
    private final Clock clock;
    private final Map<GuiMessage, LocalDateTime> receiveTimes = new IdentityHashMap<>();

    public ChatTimestampStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void record(GuiMessage message) {
        receiveTimes.computeIfAbsent(Objects.requireNonNull(message, "message"), ignored -> LocalDateTime.now(clock));
    }

    public LocalDateTime receiveTime(GuiMessage message) {
        return receiveTimes.get(message);
    }

    public void transfer(GuiMessage original, GuiMessage replacement) {
        var receiveTime = receiveTimes.remove(original);
        if (receiveTime != null) receiveTimes.put(replacement, receiveTime);
    }

    public void retainAll(List<GuiMessage> retainedMessages) {
        var retained = new IdentityHashMap<GuiMessage, Boolean>();
        for (var message : retainedMessages) retained.put(message, Boolean.TRUE);
        receiveTimes.keySet().removeIf(message -> !retained.containsKey(message));
    }

    public void clear() {
        receiveTimes.clear();
    }

    int size() {
        return receiveTimes.size();
    }
}
