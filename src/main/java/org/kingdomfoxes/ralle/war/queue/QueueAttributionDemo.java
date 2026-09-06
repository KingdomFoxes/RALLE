package org.kingdomfoxes.ralle.war.queue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Development-only, render-scoped sample rows. No Wynntils model or server state is mutated. */
final class QueueAttributionDemo {
    static final String REMOTE_IGN = "_Leoh_";
    private static final List<Template> TEMPLATES = List.of(
            new Template("ralle$queue_demo$remote", Sender.REMOTE, "Nemract", "Very High", 70_000L),
            new Template("ralle$queue_demo$self", Sender.SELF, "Detlas", "High", 152_000L),
            new Template("ralle$queue_demo$unknown", Sender.UNKNOWN, "Ragni", "Low", 193_000L)
    );

    private final LongSupplier clock;
    private boolean active;
    private long startedAtMillis;

    QueueAttributionDemo(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized ToggleResult toggle(boolean available) {
        if (!available) {
            clear();
            return ToggleResult.UNAVAILABLE;
        }
        active = !active;
        if (active) startedAtMillis = clock.getAsLong();
        return active ? ToggleResult.ENABLED : ToggleResult.DISABLED;
    }

    synchronized List<Timer> timers() {
        if (!active) return List.of();
        long now = clock.getAsLong();
        var timers = TEMPLATES.stream()
                .map(template -> new Timer(template.key(), startedAtMillis + template.durationMillis()))
                .filter(timer -> timer.timerEndMillis() > now)
                .toList();
        if (timers.isEmpty()) active = false;
        return timers;
    }

    synchronized Optional<Row> row(String key) {
        if (!active) return Optional.empty();
        return TEMPLATES.stream()
                .filter(template -> template.key().equals(key))
                .findFirst()
                .map(template -> new Row(template.sender(), template.territory(), template.defense()));
    }

    synchronized void clear() {
        active = false;
        startedAtMillis = 0L;
    }

    enum ToggleResult { ENABLED, DISABLED, UNAVAILABLE }
    enum Sender { SELF, REMOTE, UNKNOWN }
    record Timer(String key, long timerEndMillis) {}
    record Row(Sender sender, String territory, String defense) {}
    private record Template(String key, Sender sender, String territory, String defense, long durationMillis) {}
}
