package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared Fox-branded title header for RALLE-owned screens. */
final class RalleHeader {
    static final int HEIGHT = 41;
    private static final Identifier FOX_EMBLEM = Identifier.fromNamespaceAndPath("ralle", "textures/gui/fox_overlay.png");

    private RalleHeader() {}

    static FlowLayout create(Component screenTitle) {
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(HEIGHT));
        header.verticalAlignment(VerticalAlignment.CENTER).surface(RalleSurfaces.FRAMED_NAVY);
        var identity = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        identity.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.left(10));
        var emblem = UIComponents.texture(FOX_EMBLEM, 0, 0, 777, 1186, 777, 1186).blend(true);
        emblem.sizing(Sizing.fixed(18), Sizing.fixed(28)).margins(Insets.right(10));
        identity.child(emblem);
        identity.child(UIComponents.label(RalleTheme.heading(screenTitle)).lineHeight(16).shadow(false).color(RalleTheme.text()));
        header.child(identity).child(UIComponents.spacer()).child(new HeaderAccentComponent(50, HEIGHT));
        return header;
    }
}
