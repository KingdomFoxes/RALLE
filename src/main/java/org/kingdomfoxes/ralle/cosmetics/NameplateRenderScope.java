package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringDecomposer;

/** Synchronous player-render context, including replacement labels; always restore across nesting and failures. */
public final class NameplateRenderScope implements AutoCloseable {
    private static final ThreadLocal<NameplateRenderScope> CURRENT = new ThreadLocal<>();
    private final NameplateRenderScope previous;
    private final NameplateStyle style;
    private final CosmeticAppearance appearance;
    private final String ign;
    private final Component originalLabel, score;
    private final SubmitNodeCollector collector;
    private Component submittingLabel;

    private NameplateRenderScope(NameplateStyle style, CosmeticAppearance appearance, String ign,
                                Component originalLabel, Component score, SubmitNodeCollector collector) {
        this.previous = CURRENT.get();
        this.style = style;
        this.appearance = appearance;
        this.ign = ign;
        this.originalLabel = originalLabel;
        this.score = score;
        this.collector = collector;
        CURRENT.set(this);
    }

    public static NameplateRenderScope open(NameplateStyle style, CosmeticAppearance appearance, String ign,
                                            Component originalLabel, Component score, SubmitNodeCollector collector) {
        return new NameplateRenderScope(style, appearance, ign, originalLabel, score, collector);
    }

    public static NameplateRenderScope current() { return CURRENT.get(); }
    public NameplateStyle style() { return style; }
    public CosmeticAppearance appearance() { return appearance; }
    public SubmitNodeCollector collector() { return collector; }
    public Component submittingLabel() { return submittingLabel; }

    /** Only the username row gets material; leave score, level, account-role and unrelated labels alone. */
    public boolean decorates(Component label) {
        if (style == null || appearance == null || appearance.treatment() != CosmeticAppearance.Treatment.PLATE
                || label == null || label == score || submittingLabel != null) return false;
        if (label == originalLabel) return true;
        if (ign == null || ign.isEmpty()) return false;
        String plain = StringDecomposer.getPlainText(label);
        int start = plain.indexOf(ign), end = start + ign.length();
        return start >= 0 && (start == 0 || !usernameCharacter(plain.charAt(start - 1)))
                && (end == plain.length() || !usernameCharacter(plain.charAt(end)));
    }

    private static boolean usernameCharacter(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9' || value == '_';
    }

    /** Reentrancy guard while the final label is routed into the later text collection. */
    public void submit(Component label, Runnable submission) {
        Component previousLabel = submittingLabel;
        submittingLabel = label;
        try { submission.run(); }
        finally { submittingLabel = previousLabel; }
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
