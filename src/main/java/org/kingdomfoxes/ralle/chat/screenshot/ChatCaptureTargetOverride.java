package org.kingdomfoxes.ralle.chat.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jspecify.annotations.Nullable;

/** Render-thread-only target override used for the isolated chat render pass. */
public final class ChatCaptureTargetOverride {
    private static final ThreadLocal<RenderTarget> TARGET = new ThreadLocal<>();

    private ChatCaptureTargetOverride() {}

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
