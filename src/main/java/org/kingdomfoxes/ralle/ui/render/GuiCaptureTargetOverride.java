package org.kingdomfoxes.ralle.ui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jspecify.annotations.Nullable;

/** Render-thread-only target override for isolated GUI captures, with no feature state. */
public final class GuiCaptureTargetOverride {
    private static final ThreadLocal<RenderTarget> TARGET = new ThreadLocal<>();

    private GuiCaptureTargetOverride() {}

    public static void runWith(RenderTarget target, Runnable action) {
        if (TARGET.get() != null) throw new IllegalStateException("Nested RALLE capture render");
        TARGET.set(target);
        try {
            action.run();
        } finally {
            TARGET.remove();
        }
    }

    @Nullable
    public static RenderTarget current() {
        return TARGET.get();
    }
}
