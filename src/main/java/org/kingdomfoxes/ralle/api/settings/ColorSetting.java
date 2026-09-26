package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

/** Local RGB/rainbow style. Legacy #RRGGBB values remain solid; animation appends ;rainbow or ;chroma. */
public final class ColorSetting extends Setting<HighlightStyle> {
    public ColorSetting(String id, Component title, Component description, int rgb) {
        super(id, title, description, new HighlightStyle(rgb, false));
    }

    @Override public String serialize() {
        return value().hex() + (value().rainbow() ? ";rainbow" : value().chroma() ? ";chroma" : "");
    }

    @Override protected HighlightStyle deserialize(String value) {
        boolean rainbow = value.endsWith(";rainbow");
        boolean chroma = value.endsWith(";chroma");
        String hex = rainbow ? value.substring(0, value.length() - 8) : chroma ? value.substring(0, value.length() - 7) : value;
        return HighlightStyle.parse(hex, rainbow, chroma);
    }
}
