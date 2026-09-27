package org.kingdomfoxes.ralle.cosmetics.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAvatarState;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements CosmeticAvatarState {
    @Unique private NameplateStyle ralle$style;
    @Unique private CosmeticAppearance ralle$appearance;
    @Unique private String ralle$ign;

    @Override public void ralle$cosmetic(NameplateStyle style, CosmeticAppearance appearance, String ign) {
        ralle$style = style;
        ralle$appearance = appearance;
        ralle$ign = ign;
    }
    @Override public NameplateStyle ralle$style() { return ralle$style; }
    @Override public CosmeticAppearance ralle$appearance() { return ralle$appearance; }
    @Override public String ralle$ign() { return ralle$ign; }
}
