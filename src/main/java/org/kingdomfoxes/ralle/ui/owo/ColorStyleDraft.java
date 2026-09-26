package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

/** Synchronized RGB/hex/rainbow draft used by create and edit dialogs. */
public final class ColorStyleDraft {
    private final HighlightStyle original;
    private int rgb;
    private boolean rainbow;
    private boolean chroma;

    public ColorStyleDraft(HighlightStyle original) {
        this.original = original;
        this.rgb = original.rgb();
        this.rainbow = original.rainbow();
        this.chroma = original.chroma();
    }

    public void rgb(int rgb) {
        this.rgb = new HighlightStyle(rgb, rainbow, chroma).rgb();
    }

    public void hex(String hex) {
        this.rgb = HighlightStyle.parse(hex, rainbow, chroma).rgb();
    }

    public String hex() { return "#%06X".formatted(rgb); }
    public int rgb() { return rgb; }
    public boolean rainbow() { return rainbow; }
    public void rainbow(boolean rainbow) { this.rainbow = rainbow; if (rainbow) chroma = false; }
    public boolean chroma() { return chroma; }
    public void chroma(boolean chroma) { this.chroma = chroma; if (chroma) rainbow = false; }
    public HighlightStyle style() { return new HighlightStyle(rgb, rainbow, chroma); }
    public HighlightStyle cancel() { return original; }
}
