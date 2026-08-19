package org.kingdomfoxes.ralle.ui.owo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Local-only state for the alliance requirement preview.
 *
 * <p>This deliberately has no LFG service, gateway, command, or persistence dependency.</p>
 */
final class AllianceRequirementPreview {
    static final String ENABLE_PROPERTY = "ralle.lfg.allyPreview";
    static final long SENDING_DURATION_NANOS = 900_000_000L;
    private static final long LOADING_FRAME_NANOS = 160_000_000L;

    private final LongSupplier nanoTime;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long version;

    AllianceRequirementPreview(List<Guild> guilds, LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        for (var guild : List.copyOf(guilds)) {
            if (entries.putIfAbsent(guild.id(), new Entry(guild)) != null) {
                throw new IllegalArgumentException("Duplicate preview guild id: " + guild.id());
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("At least one preview guild is required");
    }

    static boolean enabled() {
        return enabled(System.getProperty(ENABLE_PROPERTY));
    }

    static boolean enabled(String value) {
        return Boolean.parseBoolean(value);
    }

    static List<Guild> defaultGuilds() {
        return List.of(new Guild("seq", "SEQ", "SEQ"));
    }

    List<Row> rows() {
        return entries.values().stream()
                .map(entry -> new Row(entry.guild, entry.state))
                .toList();
    }

    boolean request(String guildId) {
        var entry = entries.get(guildId);
        if (entry == null || entry.state != State.REQUESTABLE) return false;
        entry.state = State.SENDING;
        entry.sendingStartedAt = nanoTime.getAsLong();
        version++;
        return true;
    }

    boolean tick() {
        long now = nanoTime.getAsLong();
        boolean changed = false;
        for (var entry : entries.values()) {
            if (entry.state != State.SENDING
                    || now - entry.sendingStartedAt < SENDING_DURATION_NANOS) continue;
            entry.state = State.WAITING;
            changed = true;
        }
        if (changed) version++;
        return changed;
    }

    boolean simulateAcceptance(String guildId) {
        var entry = entries.get(guildId);
        if (entry == null || entry.state != State.WAITING) return false;
        entry.state = State.ALLIED;
        version++;
        return true;
    }

    boolean readyToJoin() {
        return entries.values().stream().allMatch(entry -> entry.state == State.ALLIED);
    }

    boolean sending() {
        return entries.values().stream().anyMatch(entry -> entry.state == State.SENDING);
    }

    long loadingFrame() {
        return Math.floorDiv(nanoTime.getAsLong(), LOADING_FRAME_NANOS);
    }

    String loadingIndicator(String guildId) {
        var entry = entries.get(guildId);
        if (entry == null || entry.state != State.SENDING) return "";
        int dots = (int) Math.floorMod(
                Math.floorDiv(nanoTime.getAsLong() - entry.sendingStartedAt, LOADING_FRAME_NANOS), 3L) + 1;
        return "·".repeat(dots);
    }

    long version() {
        return version;
    }

    enum State {
        REQUESTABLE,
        SENDING,
        WAITING,
        ALLIED
    }

    record Guild(String id, String name, String tag) {
        Guild {
            id = requireText(id, "id");
            name = requireText(name, "name");
            tag = requireText(tag, "tag");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Preview guild " + field + " must not be blank");
            }
            return value.strip();
        }
    }

    record Row(Guild guild, State state) {}

    private static final class Entry {
        private final Guild guild;
        private State state = State.REQUESTABLE;
        private long sendingStartedAt;

        private Entry(Guild guild) {
            this.guild = guild;
        }
    }
}
