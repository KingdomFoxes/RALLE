package org.kingdomfoxes.ralle.cosmetics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.LiquidComponentTint;
import org.kingdomfoxes.ralle.cosmetics.NameplateRenderScope;
import org.kingdomfoxes.ralle.cosmetics.WorldNameplateBounds;
import org.kingdomfoxes.ralle.cosmetics.WorldNameplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared final submission path used by both Minecraft and Wynntils replacement tags. */
@Mixin(NameTagFeatureRenderer.Storage.class)
abstract class NameTagMaterialMixin {
    @Unique private static final Logger ralle$logger = LoggerFactory.getLogger("RALLE/Nameplates");
    @Unique private static boolean ralle$reportedFailure;

    @WrapMethod(method = "add", require = 1)
    private void ralle$routeFinalLabel(PoseStack pose, Vec3 attachment, int y, Component label, boolean seeThrough,
                                       int light, double distance, CameraRenderState camera, Operation<Void> original) {
        var scope = NameplateRenderScope.current();
        if (scope == null || attachment == null || !scope.decorates(label)) {
            original.call(pose, attachment, y, label, seeThrough, light, distance, camera);
            return;
        }
        Component tinted;
        try {
            tinted = LiquidComponentTint.apply(label, Minecraft.getInstance().font, (x, row) -> 0xffffffff);
        } catch (RuntimeException failure) {
            ralle$warn(failure);
            original.call(pose, attachment, y, label, seeThrough, light, distance, camera);
            return;
        }
        // Even a replacement label now follows this route, exactly once, after material geometry.
        scope.submit(tinted, () -> scope.collector().order(1)
                .submitNameTag(pose, attachment, y, tinted, seeThrough, light, distance, camera));
    }

    @Inject(method = "add", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE), require = 1)
    private void ralle$fitPlateToFinalPose(PoseStack pose, Vec3 attachment, int y, Component label, boolean seeThrough,
                                          int light, double distance, CameraRenderState camera, CallbackInfo ci) {
        var scope = NameplateRenderScope.current();
        if (scope == null || scope.submittingLabel() != label) return;
        try {
            var appearance = scope.appearance();
            var bounds = WorldNameplateBounds.measure(Minecraft.getInstance().font, label, y);
            var texture = RalleClient.context().cosmeticTextures().texture(scope.style(), appearance.resolution(), System.nanoTime());
            // At this anchor vanilla has applied the final attachment, camera rotation and scale,
            // including Wynntils' scale hook. The plate and glyphs therefore share the same pose.
            WorldNameplateRenderer.submit(scope.collector().order(0), pose, texture, bounds, seeThrough, light);
        } catch (RuntimeException failure) {
            ralle$warn(failure);
        }
    }

    @Unique private static void ralle$warn(RuntimeException failure) {
        if (!ralle$reportedFailure) {
            ralle$reportedFailure = true;
            ralle$logger.warn("Could not decorate a supporter nameplate; keeping vanilla lettering", failure);
        }
    }
}
