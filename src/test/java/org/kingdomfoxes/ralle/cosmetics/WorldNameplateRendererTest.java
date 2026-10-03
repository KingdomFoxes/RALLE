package org.kingdomfoxes.ralle.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldNameplateRendererTest {
    @Test void finalScaledPoseAndFullIconBoundsAreUsedForTheMaterialAndFrame() {
        var font = CosmeticTestFont.create();
        var label = net.minecraft.network.chat.Component.literal("\ue001 Player");
        var bounds = WorldNameplateBounds.measure(font, label, 0);
        var pose = new PoseStack();
        pose.translate(3, 4, 5);
        pose.scale(0.05f, -0.05f, 0.05f); // A replacement renderer's 2x name-tag scale.
        List<List<Vector3f>> submissions = capture(pose, bounds, true);
        assertEquals(4, submissions.size());
        assertEquals(4, submissions.get(0).size());
        assertEquals(16, submissions.get(1).size());
        var material = submissions.get(0);
        assertEquals(3 + bounds.left() * 0.05f, material.get(0).x, 0.00001);
        assertEquals(3 + bounds.right() * 0.05f, material.get(1).x, 0.00001);
        assertEquals(4 - bounds.bottom() * 0.05f, material.get(0).y, 0.00001);
        for (var submitted : submissions) for (var vertex : submitted)
            assertTrue(vertex.z < 5); // Both passes stay behind glyphs in the same final pose.
        assertTrue(submissions.get(1).stream().anyMatch(vertex -> vertex.x < material.get(0).x));
        assertTrue(submissions.get(1).stream().anyMatch(vertex -> vertex.x > material.get(1).x));
    }

    @Test void discreteNameTagsDoNotAddAnExtraSeeThroughPass() {
        assertEquals(2, capture(new PoseStack(), new WorldNameplateBounds(-20, 20, -1, 9), false).size());
    }

    private static List<List<Vector3f>> capture(PoseStack pose, WorldNameplateBounds bounds, boolean seeThrough) {
        List<List<Vector3f>> submissions = new ArrayList<>();
        var collector = (OrderedSubmitNodeCollector) Proxy.newProxyInstance(OrderedSubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{OrderedSubmitNodeCollector.class}, (proxy, method, args) -> {
                    assertEquals("submitCustomGeometry", method.getName());
                    List<Vector3f> vertices = new ArrayList<>();
                    submissions.add(vertices);
                    var consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                            new Class<?>[]{VertexConsumer.class}, (vertexProxy, vertexMethod, vertexArgs) -> {
                                if (vertexMethod.isDefault()) return InvocationHandler.invokeDefault(vertexProxy, vertexMethod, vertexArgs);
                                if (vertexMethod.getName().equals("addVertex"))
                                    vertices.add(new Vector3f((float) vertexArgs[0], (float) vertexArgs[1], (float) vertexArgs[2]));
                                return vertexProxy;
                            });
                    ((SubmitNodeCollector.CustomGeometryRenderer) args[2]).render(((PoseStack) args[0]).last(), consumer);
                    return null;
                });
        WorldNameplateRenderer.submit(collector, pose, Identifier.fromNamespaceAndPath("ralle", "test_material"), bounds, seeThrough, 0);
        return submissions;
    }
}
