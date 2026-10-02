package org.kingdomfoxes.ralle.cosmetics;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** Exercise Minecraft's real text preparation without a GPU, including an icon with a small advance. */
final class CosmeticTestFont {
    static Font create() {
        var source = new GlyphSource() {
            @Override public BakedGlyph getGlyph(int codepoint) {
                boolean icon = codepoint == 0xe001;
                return new BakedGlyph() {
                    @Override public GlyphInfo info() { return GlyphInfo.simple(icon ? 2 : 6); }
                    @Override public TextRenderable.Styled createGlyph(float x, float y, int color, int shadow,
                                                                       Style style, float bold, float shadowOffset) {
                        return new Glyph(x + (icon ? -8 : 0), y + (icon ? -3 : 0),
                                x + (icon ? 10 : 5) + bold, y + (icon ? 10 : 8), style);
                    }
                };
            }
            @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) { return getGlyph(codepoint); }
        };
        return new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) { return source; }
            @Override public EffectGlyph effect() { throw new AssertionError("No decorations or background needed"); }
        });
    }

    private record Glyph(float left, float top, float right, float bottom, Style style) implements TextRenderable.Styled {
        @Override public void render(Matrix4f matrix, VertexConsumer consumer, int light, boolean shadow) {
            throw new AssertionError("Measurement never renders");
        }
        @Override public RenderType renderType(Font.DisplayMode mode) { throw new AssertionError("Measurement only"); }
        @Override public GpuTextureView textureView() { throw new AssertionError("Measurement only"); }
        @Override public RenderPipeline guiPipeline() { throw new AssertionError("Measurement only"); }
    }
}
