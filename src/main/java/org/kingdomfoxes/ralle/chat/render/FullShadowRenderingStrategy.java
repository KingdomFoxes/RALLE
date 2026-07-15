package org.kingdomfoxes.ralle.chat.render;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import org.slf4j.Logger;

/**
 * Selects one Full-shadow implementation for the lifetime of the client.
 * Once the compositor fails, subsequent frames use the batched glyph fallback.
 */
public final class FullShadowRenderingStrategy {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean compositorAvailable = true;
    private static boolean failureLogged;

    private FullShadowRenderingStrategy() {}

    public static void registerCompositor() {
        try {
            RalleChatRenderPipelines.initialize();
            SpecialGuiElementRegistry.register(context -> new FullShadowMaskRenderer(context.vertexConsumers()));
        } catch (Throwable failure) {
            disableCompositor("registering the GUI compositor", failure);
        }
    }

    public static boolean compositorAvailable() {
        return compositorAvailable;
    }

    public static void disableCompositor(String operation, Throwable failure) {
        compositorAvailable = false;
        if (failureLogged) return;

        failureLogged = true;
        LOGGER.error("RALLE Full-shadow compositor failed while {}; using the batched fallback for this session", operation, failure);
    }
}
