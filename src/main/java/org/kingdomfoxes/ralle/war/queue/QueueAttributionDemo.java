package org.kingdomfoxes.ralle.war.queue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Development-only, render-scoped sample rows. No Wynntils model or server state is mutated. */
public final class QueueAttributionDemo {
    static final String REMOTE_IGN = "_Leoh_";
    private static final List<Template> TEMPLATES = List.of(
            new Template("ralle$queue_demo$remote", Sender.REMOTE, "Nemract", "Very High", 70_000L),
            new Template("ralle$queue_demo$self", Sender.SELF, "Detlas", "High", 152_000L),
            new Template("ralle$queue_demo$unknown", Sender.UNKNOWN, "Ragni", "Low", 193_000L)
    );

    public static net.minecraft.network.chat.Component preview(String self,
            org.kingdomfoxes.ralle.war.consumables.HighlightStyle style, long timeMillis) {
        return TEMPLATES.stream().filter(template -> template.sender() == Sender.SELF).map(template -> {
            var sender = switch (template.sender()) {
                case SELF -> Optional.of(self);
                case REMOTE -> Optional.of(REMOTE_IGN);
                case UNKNOWN -> Optional.<String>empty();
            };
            long seconds = template.durationMillis() / 1000;
            int defenseColor = switch (template.defense()) {
                case "Very High" -> 0xAA0000;
                case "High" -> 0xFF5555;
                default -> 0x55FF55;
            };
            var original = net.minecraft.network.chat.Component.empty()
                    .append(net.minecraft.network.chat.Component.literal(template.territory()).withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(net.minecraft.network.chat.Component.literal(" (" + template.defense() + ")").withStyle(s -> s.withColor(defenseColor)))
                    .append(net.minecraft.network.chat.Component.literal(" %d:%02d".formatted(seconds / 60, seconds % 60)).withStyle(net.minecraft.ChatFormatting.AQUA));
            return QueueAttributionFormatter.format(original, sender, self,
                    net.minecraft.network.chat.Component.translatable("ralle.war.queue.unknown"), style, timeMillis);
        }).findFirst().orElseThrow();
    }

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
