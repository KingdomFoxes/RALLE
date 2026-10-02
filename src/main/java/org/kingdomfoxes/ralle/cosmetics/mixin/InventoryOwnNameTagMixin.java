package org.kingdomfoxes.ralle.cosmetics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.kingdomfoxes.ralle.cosmetics.OwnNameTagPreview;
import org.kingdomfoxes.ralle.cosmetics.InventoryNameTagBounds;
import org.kingdomfoxes.ralle.cosmetics.OwnNameTagVisibility;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryScreen.class)
abstract class InventoryOwnNameTagMixin {
    @WrapMethod(method = "extractRenderState", require = 1)
    private static EntityRenderState ralle$extractOwnName(LivingEntity entity, Operation<EntityRenderState> original) {
        if (!OwnNameTagVisibility.enabled()) return original.call(entity);
        return OwnNameTagPreview.extract(() -> original.call(entity));
    }

    @WrapOperation(method = "renderEntityInInventoryFollowsMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;submitEntityRenderState(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIII)V"), require = 1)
    private static void ralle$fitOwnName(GuiGraphics graphics, EntityRenderState state, float scale,
                                        Vector3f translation, Quaternionf rotation, Quaternionf cameraAngle,
                                        int left, int top, int right, int bottom, Operation<Void> original,
                                        @Local(argsOnly = true) LivingEntity entity) {
        var client = Minecraft.getInstance();
        if (entity == client.player && state.nameTag != null && OwnNameTagVisibility.enabled()) {
            int width = client.font.width(state.nameTag);
            if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar && avatar.scoreText != null)
                width = Math.max(width, client.font.width(avatar.scoreText));
            var bounds = new InventoryNameTagBounds(left, top, right, bottom)
                    .fit(width, scale, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            left = bounds.left();
            top = bounds.top();
            right = bounds.right();
            bottom = bounds.bottom();
        }
        original.call(graphics, state, scale, translation, rotation, cameraAngle, left, top, right, bottom);
    }
}
