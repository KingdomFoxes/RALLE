package org.kingdomfoxes.ralle.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** One reusable dynamic material texture per active style and resolution; render-thread owned. */
public final class LiquidMaterialTextures implements AutoCloseable {
    private static final long FRAME_NANOS = 33_333_333L;
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private final Minecraft minecraft;
    private final Map<Key, Entry> textures = new HashMap<>();

    public LiquidMaterialTextures(Minecraft minecraft) { this.minecraft = minecraft; }

    /** Call only on the render thread. Hidden surfaces do not update materials. */
    public Identifier texture(NameplateStyle style, double resolution, long nowNanos) {
        Key key = new Key(style.id(), resolution);
        Entry entry = textures.get(key);
        if (entry == null) {
            int width = LiquidMaterial.width(resolution), height = LiquidMaterial.height(resolution);
            var image = new NativeImage(width, height, true);
            var texture = new DynamicTexture(() -> "RALLE liquid nameplate " + style.id(), image);
            var id = Identifier.fromNamespaceAndPath("ralle", "dynamic/liquid_nameplate_" + NEXT_ID.incrementAndGet());
            minecraft.getTextureManager().register(id, texture);
            entry = new Entry(id, texture, new int[width * height]);
            textures.put(key, entry);
        }
        if (entry.lastUpdateNanos == Long.MIN_VALUE || nowNanos - entry.lastUpdateNanos >= FRAME_NANOS) {
            var image = entry.texture.getPixels();
            if (image == null) throw new IllegalStateException("Liquid nameplate texture was released");
            int width = image.getWidth(), height = image.getHeight();
            LiquidMaterial.fill(style, resolution, nowNanos / 1_000_000_000d,
                    width, height, entry.frame);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                image.setPixel(x, y, entry.frame[y * width + x]);
            entry.texture.upload();
            entry.lastUpdateNanos = nowNanos;
        }
        return entry.id;
    }

    /** Samples the same frame used by the dynamic texture; coordinates wrap within the recipe surface. */
    public int sample(NameplateStyle style, double resolution, int logicalX, int logicalY, long nowNanos) {
        texture(style, resolution, nowNanos);
        Entry entry = textures.get(new Key(style.id(), resolution));
        int width = LiquidMaterial.width(resolution), height = LiquidMaterial.height(resolution);
        int x = Math.floorMod((int) Math.round(logicalX / resolution), width);
        int y = Math.floorMod((int) Math.round(logicalY / resolution), height);
        return entry.frame[y * width + x];
    }

    /** Invalidate on disable and resource reload. */
    @Override public void close() {
        textures.values().forEach(entry -> minecraft.getTextureManager().release(entry.id));
        textures.clear();
    }

    private record Key(String styleId, double resolution) {}
    private static final class Entry {
        final Identifier id;
        final DynamicTexture texture;
        final int[] frame;
        long lastUpdateNanos = Long.MIN_VALUE;
        Entry(Identifier id, DynamicTexture texture, int[] frame) {
            this.id = id;
            this.texture = texture;
            this.frame = frame;
        }
    }
}
