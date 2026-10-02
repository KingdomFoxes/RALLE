package org.kingdomfoxes.ralle.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Material geometry in an already transformed nametag pose, behind the actual final glyphs. */
public final class WorldNameplateRenderer {
    private static final float DEPTH = -0.005F;
    private WorldNameplateRenderer() {}

    public static void submit(OrderedSubmitNodeCollector collector, PoseStack pose, Identifier texture,
                              WorldNameplateBounds bounds, boolean seeThrough, int light) {
        if (seeThrough) {
            fill(collector, pose, RenderTypes.textSeeThrough(texture), bounds, light, 0x80ffffff);
            border(collector, pose, RenderTypes.textBackgroundSeeThrough(), bounds, light, 0x80ffffff);
        }
        fill(collector, pose, RenderTypes.text(texture), bounds, light, 0xffffffff);
        border(collector, pose, RenderTypes.textBackground(), bounds, light, 0xffffffff);
    }

    private static void fill(OrderedSubmitNodeCollector collector, PoseStack pose, RenderType renderType,
                              WorldNameplateBounds bounds, int light, int tint) {
        collector.submitCustomGeometry(pose, renderType, (matrix, vertices) -> {
            vertices.addVertex(matrix, bounds.left(), bounds.bottom(), DEPTH).setColor(tint).setUv(0, 1).setLight(light);
            vertices.addVertex(matrix, bounds.right(), bounds.bottom(), DEPTH).setColor(tint).setUv(1, 1).setLight(light);
            vertices.addVertex(matrix, bounds.right(), bounds.top(), DEPTH).setColor(tint).setUv(1, 0).setLight(light);
            vertices.addVertex(matrix, bounds.left(), bounds.top(), DEPTH).setColor(tint).setUv(0, 0).setLight(light);
        });
    }

    private static void border(OrderedSubmitNodeCollector collector, PoseStack pose, RenderType renderType,
                                WorldNameplateBounds bounds, int light, int tint) {
        collector.submitCustomGeometry(pose, renderType, (matrix, vertices) -> {
            float left = bounds.left(), right = bounds.right(), top = bounds.top(), bottom = bounds.bottom();
            for (float[] edge : new float[][] {
                    {left - 1, right + 1, top - 1, top}, {left - 1, right + 1, bottom, bottom + 1},
                    {left - 1, left, top, bottom}, {right, right + 1, top, bottom}}) {
                vertices.addVertex(matrix, edge[0], edge[3], DEPTH).setColor(tint).setLight(light);
                vertices.addVertex(matrix, edge[1], edge[3], DEPTH).setColor(tint).setLight(light);
                vertices.addVertex(matrix, edge[1], edge[2], DEPTH).setColor(tint).setLight(light);
                vertices.addVertex(matrix, edge[0], edge[2], DEPTH).setColor(tint).setLight(light);
            }
        });
    }
}
