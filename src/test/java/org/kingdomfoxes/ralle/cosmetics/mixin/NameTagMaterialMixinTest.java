package org.kingdomfoxes.ralle.cosmetics.mixin;

import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.NameplateRenderScope;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class NameTagMaterialMixinTest {
    private final NameTagMaterialMixin hook = new NameTagMaterialMixin() {};

    @Test void materialReplacesBackgroundInEveryPassWithoutAffectingOtherLabels() throws Exception {
        var label = Component.literal("Player");
        var finalLabel = Component.literal("\ue001 Player [Fox]");
        var score = Component.literal("123 points");
        var style = NameplateStyle.byId("supporter-gold").orElseThrow();
        var appearance = new CosmeticAppearance(1);
        Method background = NameTagMaterialMixin.class.getDeclaredMethod("ralle$replaceVanillaBackground",
                Matrix4f.class, float.class, float.class, Component.class,
                int.class, int.class, int.class, double.class);
        background.setAccessible(true);
        assertEquals(0x40000000, background(background, finalLabel, 0x40000000));
        try (var scope = NameplateRenderScope.open(style, appearance, "Player", label, score, null)) {
            assertEquals(0x40000000, background(background, finalLabel, 0x40000000));
            scope.submit(finalLabel, () -> {
                for (int opacity : new int[] {0, 0x40000000, 0xff000000}) {
                    // Vanilla defaults and externally overridden opacity must all yield no rectangle.
                    assertEquals(0, background(background, finalLabel, opacity));
                    assertEquals(opacity, background(background, score, opacity));
                    assertEquals(opacity, background(background, Component.literal("Other"), opacity));
                }
                try (var nested = NameplateRenderScope.open(null, null, "Other", null, null, null)) {
                    assertEquals(0x40000000, background(background, finalLabel, 0x40000000));
                }
                assertEquals(0, background(background, finalLabel, 0x40000000));
            });
            assertEquals(0x40000000, background(background, finalLabel, 0x40000000));
        }
        assertEquals(0x40000000, background(background, finalLabel, 0x40000000));
    }

    private int background(Method method, Component label, int opacity) {
        try {
            return (int) method.invoke(hook, new Matrix4f(), 0f, 0f, label, 0, -1, opacity, 0d);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
