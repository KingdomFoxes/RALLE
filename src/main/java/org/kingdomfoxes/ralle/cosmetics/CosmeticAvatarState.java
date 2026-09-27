package org.kingdomfoxes.ralle.cosmetics;

/** Transient decoration attached to one vanilla player render state. */
public interface CosmeticAvatarState {
    void ralle$cosmetic(NameplateStyle style, CosmeticAppearance appearance, String ign);
    NameplateStyle ralle$style();
    CosmeticAppearance ralle$appearance();
    String ralle$ign();
}
