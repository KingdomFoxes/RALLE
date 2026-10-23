package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared Fox-branded title header for RALLE-owned screens. */
final class RalleHeader {
    static final int HEIGHT = 41;
    private static final int EMBLEM_WIDTH = 18;
    private static final int EMBLEM_HEIGHT = 28;
    private static final int MAX_EMBLEM_SCALE = 16;

    private RalleHeader() {}

    static FlowLayout create(Component screenTitle) {
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(HEIGHT));
        header.verticalAlignment(VerticalAlignment.CENTER).surface(RalleSurfaces.FRAMED_NAVY);
        var identity = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        identity.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.left(10));
        // Screens rebuild on GUI-scale changes. Select a prefiltered texture with one texel
        // per framebuffer pixel instead of sampling isolated points from the large crest.
        int scale = Math.clamp((int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()),
                1, MAX_EMBLEM_SCALE);
        int textureWidth = (EMBLEM_WIDTH + 1) * scale;
        int textureHeight = (EMBLEM_HEIGHT + 1) * scale;
        var texture = Identifier.fromNamespaceAndPath("ralle", "textures/gui/header/fox_" + scale + "x.png");
        var emblem = UIComponents.texture(texture, 0, 0, textureWidth, textureHeight,
                textureWidth, textureHeight).blend(true);
        // The baked shadow has a one-pixel right/bottom gutter. Compensating margins
        // preserve the crest's position, centering, and original 18 x 28 footprint.
        emblem.sizing(Sizing.fixed(EMBLEM_WIDTH + 1), Sizing.fixed(EMBLEM_HEIGHT + 1))
                .margins(Insets.of(0, -1, 0, 9));
        identity.child(emblem);
        identity.child(new HeaderTitleComponent(screenTitle).lineHeight(16).color(RalleTheme.text()));
        header.child(identity).child(UIComponents.spacer()).child(new HeaderAccentComponent(50, HEIGHT));
        return header;
    }
}
