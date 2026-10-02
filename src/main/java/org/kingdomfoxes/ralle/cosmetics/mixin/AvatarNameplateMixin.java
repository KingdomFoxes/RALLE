package org.kingdomfoxes.ralle.cosmetics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAvatarState;
import org.kingdomfoxes.ralle.cosmetics.NameplateRenderScope;
import org.kingdomfoxes.ralle.cosmetics.OwnNameTagVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carry cosmetic identity through vanilla and replacement nametag submissions; never draw an early guessed label. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarNameplateMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"), cancellable = true, require = 1)
    private void ralle$showOwnName(Avatar avatar, double distance, CallbackInfoReturnable<Boolean> callback) {
        if (OwnNameTagVisibility.show(avatar)) callback.setReturnValue(true);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    private void ralle$clearPreviousCosmetic(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((CosmeticAvatarState) state).ralle$cosmetic(null, null, null);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void ralle$resolveVisibleCosmetic(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null || !RalleClient.initialized()) return;
        var identity = RalleClient.cosmetic(avatar.getUUID());
        if (identity == null || identity.selectedStyle() == null) return;
        var style = identity.selectedStyle();
        var appearance = RalleClient.cosmeticAppearance(style);
        if (appearance != null)
            ((CosmeticAvatarState) state).ralle$cosmetic(style, appearance, avatar.getName().getString());
    }

    @WrapMethod(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", require = 1)
    private void ralle$scopeFinalNameTag(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                        CameraRenderState camera, Operation<Void> original) {
        var decoration = (CosmeticAvatarState) state;
        if (decoration.ralle$style() == null && NameplateRenderScope.current() == null) {
            original.call(state, pose, collector, camera);
            return;
        }
        var scope = NameplateRenderScope.open(decoration.ralle$style(), decoration.ralle$appearance(),
                decoration.ralle$ign(), state.nameTag, state.scoreText, collector);
        try {
            // This includes Wynntils' cancellable event callback and the labels it submits itself.
            original.call(state, pose, collector, camera);
        } finally {
            scope.close();
        }
    }
}
