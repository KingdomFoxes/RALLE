package org.kingdomfoxes.ralle.war.queue;

import java.util.Objects;

/** Account/guild scope survives missing identity data during world and character loading. */
final class QueueAttributionSession {
    private final QueueAttributionTracker tracker;
    private String account = "";
    private String guild = "";

    QueueAttributionSession(QueueAttributionTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker);
    }

    boolean update(String accountId, String guildName) {
        String nextAccount = Objects.requireNonNullElse(accountId, "").strip();
        String nextGuild = Objects.requireNonNullElse(guildName, "").strip();
        if (nextAccount.isEmpty()) return false;
        if (!account.equals(nextAccount)) {
            clear();
            account = nextAccount;
        }
        if (nextGuild.isEmpty()) return false;
        if (!guild.isEmpty() && !guild.equals(nextGuild)) tracker.clear();
        guild = nextGuild;
        return true;
    }

    void clear() {
        tracker.clear();
        account = "";
        guild = "";
    }
}
