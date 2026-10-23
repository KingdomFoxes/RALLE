package org.kingdomfoxes.ralle.classes;

/** Resource-pack potion model and tint received in the character book; no copied texture assets. */
public record ClassSprite(float model, int tint) {
    public ClassSprite {
        if (!Float.isFinite(model) || model < 0 || model > 65535 || tint < 0 || tint > 0xFFFFFF)
            throw new IllegalArgumentException("Invalid class sprite");
    }
}
