package org.kingdomfoxes.ralle.cosmetics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Avatar;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAvatarState;
import org.kingdomfoxes.ralle.cosmetics.LiquidComponentTint;
import org.kingdomfoxes.ralle.cosmetics.UsernameSpan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Decorates only the vanilla-extracted visible player name tag. Vanilla still submits the tag once. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarNameplateMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    private void ralle$clearPreviousCosmetic(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((CosmeticAvatarState) state).ralle$cosmetic(null, null, null);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void ralle$decorateVisibleName(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null || !RalleClient.initialized()) return;
        var identity = RalleClient.cosmetic(avatar.getUUID());
        if (identity == null || identity.selectedStyle() == null) return;
        var style = identity.selectedStyle();
        var appearance = RalleClient.cosmeticAppearance(style);
        if (appearance == null) return;
        ((CosmeticAvatarState) state).ralle$cosmetic(style, appearance, avatar.getName().getString());
        var original = state.nameTag;
        try {
            long now = System.nanoTime();
            state.nameTag = LiquidComponentTint.apply(original, Minecraft.getInstance().font,
                    (x, y) -> {
                        if (appearance.treatment() == CosmeticAppearance.Treatment.TEXT)
                            return RalleClient.context().cosmeticTextures().sample(style, appearance.resolution(),
                                    x * 256 / Math.max(1, Minecraft.getInstance().font.width(original)), y * 64 / 9, now);
                        return 0xffffffff;
                    });
        } catch (RuntimeException ignored) {
            ((CosmeticAvatarState) state).ralle$cosmetic(null, null, null);
            state.nameTag = original.copy().withStyle(value -> value.withColor(0xffffff));
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
    private void ralle$submitLiquidPlate(AvatarRenderState state, PoseStack pose,
                                          SubmitNodeCollector collector, CameraRenderState camera,
                                          CallbackInfo ci) {
        if (state.nameTag == null || state.nameTagAttachment == null || !RalleClient.initialized()) return;
        var decoration = (CosmeticAvatarState) state;
        var style = decoration.ralle$style();
        var appearance = decoration.ralle$appearance();
        if (style == null || appearance == null ||
                appearance.treatment() != CosmeticAppearance.Treatment.PLATE
                        && appearance.usernameOutlinePixels() == 0) return;
        try {
            long now = System.nanoTime();
            var texture = appearance.treatment() == CosmeticAppearance.Treatment.PLATE
                    ? RalleClient.context().cosmeticTextures().texture(style, appearance.resolution(), now) : null;
            int width = Minecraft.getInstance().font.width(state.nameTag);
            if (width < 1) return;
            float left = -width / 2.0F - 1;
            float top = (state.showExtraEars ? -10 : 0) - 1, bottom = top + 10;
            pose.pushPose();
            try {
                if (state.scoreText != null) pose.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
                Vec3 attachment = state.nameTagAttachment;
                pose.translate(attachment.x, attachment.y + 0.5, attachment.z);
                pose.mulPose(camera.orientation);
                pose.scale(0.025F, -0.025F, 0.025F);
                int light = state.lightCoords;
                if (texture != null) {
                    if (!state.isDiscrete) submitPlateChunk(collector, pose, RenderTypes.textSeeThrough(texture),
                            left, left + width + 2, top, bottom, 1, light, 0x80ffffff);
                    submitPlateChunk(collector, pose, RenderTypes.text(texture),
                            left, left + width + 2, top, bottom, 1, light, 0xffffffff);
                }
                if (appearance.usernameOutlinePixels() > 0) {
                    UsernameSpan.find(state.nameTag, decoration.ralle$ign()).ifPresent(span -> {
                        float x = -width / 2f + Minecraft.getInstance().font.width(span.prefix());
                        float y = state.showExtraEars ? -10 : 0;
                        submitUsernameOutline(collector, pose, span.username(), x, y,
                                appearance.usernameOutlinePixels(), light);
                    });
                }
            } finally {
                pose.popPose();
            }
        } catch (RuntimeException ignored) {
            state.nameTag = state.nameTag.copy().withStyle(value -> value.withColor(0xffffff));
        }
    }

    private static void submitUsernameOutline(SubmitNodeCollector collector, PoseStack pose,
                                              net.minecraft.network.chat.Component username,
                                              float x, float y, int radius, int light) {
        var visual = username.getVisualOrderText();
        for (int dy = 1 - radius; dy <= radius - 1; dy++) {
            for (int dx = 1 - radius; dx <= radius - 1; dx++) {
                if (Math.abs(dx) + Math.abs(dy) > radius - 1) continue;
                collector.submitText(pose, x + dx, y + dy, visual, false,
                        Font.DisplayMode.NORMAL, 0x00000000, 0, light, 0xffffffff);
            }
        }
    }

    private static void submitPlateChunk(SubmitNodeCollector collector, PoseStack pose,
                                         net.minecraft.client.renderer.rendertype.RenderType renderType,
                                         float x0, float x1, float top, float bottom,
                                         float u1, int light, int tint) {
        collector.submitCustomGeometry(pose, renderType, (matrix, vertices) -> {
            vertices.addVertex(matrix, x0, bottom, -0.005F).setColor(tint).setUv(0, 1).setLight(light);
            vertices.addVertex(matrix, x1, bottom, -0.005F).setColor(tint).setUv(u1, 1).setLight(light);
            vertices.addVertex(matrix, x1, top, -0.005F).setColor(tint).setUv(u1, 0).setLight(light);
            vertices.addVertex(matrix, x0, top, -0.005F).setColor(tint).setUv(0, 0).setLight(light);
        });
    }
}
