package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.util.Objects;
import java.util.function.Supplier;

/** Shared resolver for player-selectable RALLE interface typography. */
public final class RalleTypography {
    public static final String VANILLA = "vanilla";
    public static final String KARLA = "karla";

    private static final FontDescription FOX_HEADER = resource("karla_bold");
    private static final FontDescription KARLA_BODY = resource("karla_bold_ui");
    private static final FontDescription KARLA_COMPACT = resource("karla_bold_ui_small");
    private static Supplier<String> selectedFont = () -> VANILLA;

    private RalleTypography() {}

    public static void bind(SettingsRegistry settings) {
        Objects.requireNonNull(settings, "settings");
        ChoiceSetting setting = settings.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class);
        selectedFont = setting::value;
    }

    public static Component foxHeader(Component component) {
        return withFont(component, FOX_HEADER);
    }

    public static Component body(Component component) {
        return KARLA.equals(selectedFont.get()) ? withFont(component, KARLA_BODY) : component.copy();
    }

    public static Component compactBody(Component component) {
        return KARLA.equals(selectedFont.get()) ? withFont(component, KARLA_COMPACT) : component.copy();
    }

    static boolean usesKarla() {
        return KARLA.equals(selectedFont.get());
    }

    private static Component withFont(Component component, FontDescription font) {
        return component.copy().withStyle(style -> style.withFont(font));
    }

    private static FontDescription resource(String path) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath("ralle", path));
    }
}
