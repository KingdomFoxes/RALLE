package org.kingdomfoxes.ralle.chat.screenshot;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.BatchedFullShadowSequence;
import org.kingdomfoxes.ralle.chat.ChatRenderLayout;
import org.kingdomfoxes.ralle.chat.ChatTextShadowStyles;
import org.kingdomfoxes.ralle.chat.mixin.GameRendererAccessor;
import org.kingdomfoxes.ralle.chat.render.FullShadowFrameCollector;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Re-renders frozen chat lines into a transparent GPU target and transfers the result locally. */
public final class TransparentChatCapture implements ChatScreenshotCapture {
    private static final Executor CLIPBOARD_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RALLE image clipboard");
        thread.setDaemon(true);
        return thread;
    });

    private final Minecraft minecraft;
    private final PlatformImageClipboard clipboard;
    private final Executor clipboardExecutor;

    public TransparentChatCapture(Minecraft minecraft) {
        this(minecraft, PlatformImageClipboard.systemDefault(), CLIPBOARD_EXECUTOR);
    }

    TransparentChatCapture(Minecraft minecraft, PlatformImageClipboard clipboard, Executor clipboardExecutor) {
        this.minecraft = minecraft;
        this.clipboard = clipboard;
        this.clipboardExecutor = clipboardExecutor;
    }

    @Override
    public void capture(Request request, Completion completion) {
        minecraft.execute(() -> {
            TextureTarget target = null;
            try {
                int density = Math.max(1, minecraft.getWindow().getGuiScale());
                int visualHeight = ChatScreenshotGeometry.captureVisualHeight(
                        request.lines().size(), request.lineHeight(), request.chatScale()
                );
                int pixelWidth = Math.max(1, request.visualWidth() * density);
                int pixelHeight = Math.max(1, visualHeight * density);
                target = new TextureTarget("RALLE transparent chat capture", pixelWidth, pixelHeight, false);
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);

                GameRendererAccessor renderer = (GameRendererAccessor) minecraft.gameRenderer;
                renderer.ralle$getGuiRenderState().reset();
                GuiGraphics graphics = new GuiGraphics(minecraft, renderer.ralle$getGuiRenderState(), 0, 0);
                float projectionCompensationX = minecraft.getWindow().getGuiScaledWidth() / (float) request.visualWidth();
                float projectionCompensationY = minecraft.getWindow().getGuiScaledHeight() / (float) visualHeight;
                graphics.pose().scale(projectionCompensationX, projectionCompensationY);
                graphics.pose().scale((float) request.chatScale(), (float) request.chatScale());
                graphics.pose().translate(0.0F, (float) (ChatScreenshotTokens.VERTICAL_PADDING / request.chatScale()));
                renderLines(graphics, request);

                TextureTarget finalTarget = target;
                GuiCaptureTargetOverride.runWith(target, () -> renderer.ralle$getGuiRenderer().render(
                        renderer.ralle$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE)
                ));
                download(finalTarget, completion);
                target = null;
            } catch (Throwable error) {
                if (target != null) target.destroyBuffers();
                completion.failed(error);
            }
        });
    }

    private void renderLines(GuiGraphics graphics, Request request) {
        int contentWidth = request.contentWidth();
        int canvasWidth = Math.max(1, (int) Math.ceil(request.visualWidth() / request.chatScale()));
        int textColor = ((int) (request.textOpacity() * 255.0F) << 24) | 0xFFFFFF;
        FullShadowFrameCollector fullShadow = request.shadow() == ChatBehaviorService.TextShadow.FULL
                ? new FullShadowFrameCollector(
                        graphics,
                        minecraft.font,
                        canvasWidth,
                        0,
                        request.lines().size() * request.lineHeight()
                )
                : null;
        for (int index = 0; index < request.lines().size(); index++) {
            FormattedCharSequence line = request.lines().get(index);
            int x = request.textOffsetX()
                    + ChatRenderLayout.horizontalOffset(request.alignment(), contentWidth, minecraft.font.width(line));
            int y = index * request.lineHeight() + request.textBaselineOffset();
            switch (request.shadow()) {
                case NONE -> graphics.drawString(minecraft.font, transform(line, Style::withoutShadow), x, y, textColor, false);
                case VANILLA -> graphics.drawString(minecraft.font, line, x, y, textColor, true);
                case PARTIAL_FULL -> graphics.drawString(minecraft.font, ChatTextShadowStyles.partialFull(line), x, y, textColor, true);
                case FULL -> renderFullShadow(graphics, fullShadow, line, x, y, textColor, request.textOpacity());
            }
        }
    }

    private void renderFullShadow(
            GuiGraphics graphics,
            FullShadowFrameCollector collector,
            FormattedCharSequence line,
            int x,
            int y,
            int textColor,
            float opacity
    ) {
        FormattedCharSequence shadow = transform(line, style -> style.withColor(0x000000).withoutShadow()
                .withClickEvent(null).withHoverEvent(null).withInsertion(null));
        if (!collector.record(line, x, y, opacity)) {
            int shadowAlpha = Math.max(0, Math.min(255, (int) (opacity * 0.25F * 255.0F)));
            graphics.drawString(minecraft.font, new BatchedFullShadowSequence(shadow), x, y, shadowAlpha << 24 | 0xFFFFFF, false);
        }
        graphics.drawString(minecraft.font, transform(line, Style::withoutShadow), x, y, textColor, false);
    }

    private void download(TextureTarget target, Completion completion) {
        var texture = target.getColorTexture();
        if (texture == null) throw new IllegalStateException("Capture target has no color texture");
        int width = target.width;
        int height = target.height;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "RALLE chat capture readback",
                9,
                (long) width * height * texture.getFormat().pixelSize()
        );
        var mapEncoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (buffer; GpuBuffer.MappedView mapped = mapEncoder.mapBuffer(buffer, true, false)) {
                var image = new NativeImage(width, height, false);
                try (image) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int abgr = mapped.data().getInt((x + y * width) * texture.getFormat().pixelSize());
                            image.setPixelABGR(x, height - y - 1, abgr);
                        }
                    }
                    byte[] rgba = toStraightAlphaRgba(image);
                    clipboardExecutor.execute(() -> publish(width, height, rgba, completion));
                }
            } catch (Throwable error) {
                completion.failed(error);
            } finally {
                target.destroyBuffers();
            }
        }, 0);
    }

    private void publish(int width, int height, byte[] rgba, Completion completion) {
        try {
            clipboard.publish(new ClipboardImage(PngEncoder.encode(width, height, rgba), width, height, rgba));
            completion.succeeded();
        } catch (Throwable error) {
            completion.failed(error);
        }
    }

    static byte[] toStraightAlphaRgba(NativeImage source) {
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(source.getWidth(), source.getHeight()), 4)];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getPixel(x, y);
                int alpha = argb >>> 24;
                if (alpha > 0 && alpha < 255) {
                    int red = Math.min(255, ((argb >>> 16) & 0xFF) * 255 / alpha);
                    int green = Math.min(255, ((argb >>> 8) & 0xFF) * 255 / alpha);
                    int blue = Math.min(255, (argb & 0xFF) * 255 / alpha);
                    argb = alpha << 24 | red << 16 | green << 8 | blue;
                }
                int offset = (x + y * source.getWidth()) * 4;
                rgba[offset] = (byte) (argb >>> 16);
                rgba[offset + 1] = (byte) (argb >>> 8);
                rgba[offset + 2] = (byte) argb;
                rgba[offset + 3] = (byte) alpha;
            }
        }
        return rgba;
    }

    private static FormattedCharSequence transform(FormattedCharSequence sequence, java.util.function.UnaryOperator<Style> transform) {
        return output -> sequence.accept((index, style, codePoint) -> output.accept(index, transform.apply(style), codePoint));
    }
}
