package org.kingdomfoxes.ralle.chat.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.RalleClient;

final class RalleChatRenderPipelines {
    private static RenderPipeline fullShadowComposite;

    private RalleChatRenderPipelines() {}

    static void initialize() {
        fullShadowComposite();
    }

    static RenderPipeline fullShadowComposite() {
        if (fullShadowComposite == null) {
            fullShadowComposite = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, "pipeline/chat_full_shadow_composite"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, "core/chat_full_shadow_composite"))
                            .build()
            );
        }
        return fullShadowComposite;
    }
}
