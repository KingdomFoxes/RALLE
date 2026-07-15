package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Marks the visual-only Full shadow so Minecraft can prepare all halo offsets
 * inside one deferred GUI text state instead of creating one state per offset.
 */
public final class BatchedFullShadowSequence implements FormattedCharSequence {
    private static final List<ShadowOffset> OFFSETS = createOffsets();

    private final FormattedCharSequence content;

    public BatchedFullShadowSequence(FormattedCharSequence content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public boolean accept(FormattedCharSink output) {
        return content.accept(output);
    }

    public Font.PreparedText prepare(Font font, float x, float y, int color) {
        var layers = new ArrayList<Font.PreparedText>(OFFSETS.size());
        for (var offset : OFFSETS) {
            layers.add(font.prepareText(
                    this,
                    x + offset.x(),
                    y + offset.y(),
                    color,
                    false,
                    false,
                    0
            ));
        }
        return new CompositePreparedText(layers);
    }

    static int layerCount() {
        return OFFSETS.size();
    }

    private static List<ShadowOffset> createOffsets() {
        var offsets = new ArrayList<ShadowOffset>(16);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                if (x * x == y * y) continue;
                offsets.add(new ShadowOffset(x / 2.0F, y / 2.0F));
            }
        }
        return List.copyOf(offsets);
    }

    private record ShadowOffset(float x, float y) {}

    private static final class CompositePreparedText implements Font.PreparedText {
        private final List<Font.PreparedText> layers;
        private final ScreenRectangle bounds;

        private CompositePreparedText(List<Font.PreparedText> layers) {
            this.layers = List.copyOf(layers);
            this.bounds = unionBounds(layers);
        }

        @Override
        public void visit(Font.GlyphVisitor visitor) {
            for (var layer : layers) {
                layer.visit(visitor);
            }
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }

        private static ScreenRectangle unionBounds(List<Font.PreparedText> layers) {
            ScreenRectangle result = null;
            for (var layer : layers) {
                var layerBounds = layer.bounds();
                if (layerBounds == null) continue;
                if (result == null) {
                    result = layerBounds;
                    continue;
                }

                int left = Math.min(result.left(), layerBounds.left());
                int top = Math.min(result.top(), layerBounds.top());
                int right = Math.max(result.right(), layerBounds.right());
                int bottom = Math.max(result.bottom(), layerBounds.bottom());
                result = new ScreenRectangle(left, top, right - left, bottom - top);
            }
            return result;
        }
    }
}
