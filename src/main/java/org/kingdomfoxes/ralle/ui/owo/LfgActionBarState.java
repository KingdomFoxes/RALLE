package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.function.LongSupplier;

/** Thread-safe presentation state for the keybind-only RALLE Action Bar. */
public final class LfgActionBarState {
    static final long LINGER_MILLIS = 350;
    static final long FADE_MILLIS = 450;

    private final LongSupplier clockMillis;
    private Entry entry;

    public LfgActionBarState() {
        this(System::currentTimeMillis);
    }

    LfgActionBarState(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public synchronized void hold(String text, Tone tone) {
        hold(text, tone, null);
    }

    public synchronized void hold(String text, Tone tone, LfgActionGlyph glyph) {
        entry = new Entry(text, null, null, null, glyph, tone, true, 0, 0);
    }

    public synchronized void holdRaid(String prefix, LfgProtocol.RaidType raid, String raidLabel, Tone tone) {
        holdRaid(prefix, raid, raidLabel, tone, null);
    }

    public synchronized void holdRaid(String prefix, LfgProtocol.RaidType raid, String raidLabel,
                                      Tone tone, LfgActionGlyph glyph) {
        entry = new Entry(null, prefix, raid, raidLabel, glyph, tone, true, 0, 0);
    }

    public synchronized void show(String text, Tone tone) {
        show(text, tone, LINGER_MILLIS);
    }

    public synchronized void show(String text, Tone tone, long lingerMillis) {
        show(text, tone, lingerMillis, null);
    }

    public synchronized void show(String text, Tone tone, LfgActionGlyph glyph) {
        show(text, tone, LINGER_MILLIS, glyph);
    }

    public synchronized void show(String text, Tone tone, long lingerMillis, LfgActionGlyph glyph) {
        long now = clockMillis.getAsLong();
        entry = new Entry(text, null, null, null, glyph, tone, false, now + lingerMillis,
                now + lingerMillis + FADE_MILLIS);
    }

    public synchronized void showRaid(String prefix, LfgProtocol.RaidType raid, String raidLabel, Tone tone) {
        showRaid(prefix, raid, raidLabel, tone, null);
    }

    public synchronized void showRaid(String prefix, LfgProtocol.RaidType raid, String raidLabel,
                                      Tone tone, LfgActionGlyph glyph) {
        long now = clockMillis.getAsLong();
        entry = new Entry(null, prefix, raid, raidLabel, glyph, tone, false, now + LINGER_MILLIS,
                now + LINGER_MILLIS + FADE_MILLIS);
    }

    public synchronized void release() {
        if (entry == null || !entry.held()) return;
        long now = clockMillis.getAsLong();
        entry = new Entry(entry.text(), entry.prefix(), entry.raid(), entry.raidLabel(), entry.glyph(),
                entry.tone(), false, now + LINGER_MILLIS, now + LINGER_MILLIS + FADE_MILLIS);
    }

    public synchronized void clear() {
        entry = null;
    }

    public synchronized Snapshot snapshot() {
        if (entry == null) return Snapshot.hidden();
        if (entry.held()) return snapshot(entry, 1d);
        long now = clockMillis.getAsLong();
        if (now >= entry.expiresAtMillis()) {
            entry = null;
            return Snapshot.hidden();
        }
        double opacity = now <= entry.fadeAtMillis() ? 1d
                : 1d - (now - entry.fadeAtMillis()) / (double) FADE_MILLIS;
        return snapshot(entry, Math.clamp(opacity, 0d, 1d));
    }

    private static Snapshot snapshot(Entry entry, double opacity) {
        return new Snapshot(true, entry.text(), entry.prefix(), entry.raid(), entry.raidLabel(),
                entry.glyph(), entry.tone(), opacity);
    }

    public enum Tone { NORMAL, ACCENT, MUTED, DANGER, POSITIVE }

    public record Snapshot(boolean visible, String text, String prefix, LfgProtocol.RaidType raid,
                           String raidLabel, LfgActionGlyph glyph, Tone tone, double opacity) {
        static Snapshot hidden() {
            return new Snapshot(false, null, null, null, null, null, Tone.NORMAL, 0d);
        }
    }

    private record Entry(String text, String prefix, LfgProtocol.RaidType raid, String raidLabel,
                         LfgActionGlyph glyph, Tone tone, boolean held,
                         long fadeAtMillis, long expiresAtMillis) {}
}
