package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Client-thread, session-only metadata shared by chat histories; never retains message contents. */
public final class ChatTimestampStore {
    public static final ChatTimestampStore SESSION = new ChatTimestampStore(Clock.systemDefaultZone());
    private final Clock clock;
    private final ReferenceQueue<GuiMessage> collected = new ReferenceQueue<>();
    private final Map<MessageIdentity, LocalDateTime> receiveTimes = new HashMap<>();
    private boolean replaying;
    private LocalDateTime replayTime;

    public ChatTimestampStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void record(GuiMessage message) {
        expunge();
        var key = new MessageIdentity(Objects.requireNonNull(message, "message"), collected);
        if (receiveTimes.containsKey(key)) return;
        // Unknown historical times remain unknown: replay must never read the current clock.
        var time = replaying ? replayTime : LocalDateTime.now(clock);
        if (time != null) receiveTimes.put(key, time);
    }

    public LocalDateTime receiveTime(GuiMessage message) {
        expunge();
        return receiveTimes.get(new MessageIdentity(message, null));
    }

    /** Wynntils enabling tabs fans one original message out to multiple new GuiMessages. */
    public void replay(GuiMessage original, Runnable action) {
        boolean previousReplaying = replaying;
        var previousTime = replayTime;
        replaying = true;
        replayTime = receiveTime(original);
        try {
            action.run();
        } finally {
            replaying = previousReplaying;
            replayTime = previousTime;
        }
    }

    public void transfer(GuiMessage original, GuiMessage replacement) {
        var time = receiveTime(original);
        if (time != null) receiveTimes.put(new MessageIdentity(replacement, collected), time);
    }

    /** Explicit clearing affects only that history, never unrelated tabs. */
    public void forgetAll(List<GuiMessage> messages) {
        for (var message : messages) receiveTimes.remove(new MessageIdentity(message, null));
        expunge();
    }

    int size() {
        expunge();
        return receiveTimes.size();
    }

    private void expunge() {
        for (var key = collected.poll(); key != null; key = collected.poll()) receiveTimes.remove(key);
    }

    private static final class MessageIdentity extends WeakReference<GuiMessage> {
        private final int hash;

        private MessageIdentity(GuiMessage message, ReferenceQueue<GuiMessage> queue) {
            super(message, queue);
            hash = System.identityHashCode(message);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            var message = get();
            return this == other || other instanceof MessageIdentity identity
                    && message != null && message == identity.get();
        }
    }
}
