package org.kingdomfoxes.ralle.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.kingdomfoxes.ralle.classes.ClassSwitchAudio;
import org.kingdomfoxes.ralle.classes.ClassSwitchFrame;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

/** Freezes the last live game frame in full black-and-white monochrome with audio cut, then closes the game. */
public final class WheelsAbyssShutdown implements AutoCloseable {
    public static final long HOLD_MILLIS = 5_000L;

    private static final class Pipeline {
        private static final RenderPipeline MONOCHROME = RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("ralle", "pipeline/wheels_monochrome"))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("ralle", "core/monochrome"))
                .withSampler("InSampler")
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .build());
    }

    private final Minecraft minecraft;
    private final ClassSwitchAudio audio;
    private final ClassSwitchFrame frame = new ClassSwitchFrame();
    private final Timeline timeline;
    private boolean savedHideGui;
    private boolean hideGuiOverridden;

    public WheelsAbyssShutdown(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.audio = new ClassSwitchAudio(() -> minecraft.getSoundManager().refreshCategoryVolume(SoundSource.MASTER));
        this.timeline = new Timeline(() -> System.nanoTime() / 1_000_000L, HOLD_MILLIS);
    }

    public boolean active() {
        return timeline.active();
    }

    public void trigger(Screen fallbackScreen) {
        if (!timeline.trigger()) return;
        savedHideGui = minecraft.options.hideGui;
        hideGuiOverridden = true;
        minecraft.options.hideGui = true;
        audio.mute();
        minecraft.getSoundManager().stop();
        minecraft.setScreen(minecraft.level != null ? null : fallbackScreen);
        releaseHeldInputs();
    }

    public void tick() {
        if (!timeline.active()) return;
        releaseHeldInputs();
        if (timeline.shouldStopNow()) {
            minecraft.stop();
        }
    }

    /** Called immediately before Minecraft blits its main render target to the window. */
    public void beforePresent() {
        if (!timeline.active()) return;
        var live = minecraft.getMainRenderTarget();
        if (timeline.awaitingCapture()) {
            frame.capture(live);
            timeline.captured();
        }
        if (frame.captured()) {
            presentMonochrome(frame.texture(), live);
        }
        if (timeline.shouldStopNow()) {
            minecraft.stop();
        }
    }

    private void presentMonochrome(RenderTarget source, RenderTarget destination) {
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RALLE wheels monochrome", destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(Pipeline.MONOCHROME);
            pass.bindTexture("InSampler", source.getColorTextureView(), sampler);
            pass.draw(0, 3);
        }
    }

    private void releaseHeldInputs() {
        for (var key : minecraft.options.keyMappings) {
            key.setDown(false);
            while (key.consumeClick()) { }
        }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
    }

    @Override
    public void close() {
        if (hideGuiOverridden) {
            hideGuiOverridden = false;
            minecraft.options.hideGui = savedHideGui;
        }
        frame.close();
        try {
            audio.close();
        } catch (RuntimeException ignored) {
            // Shutting down; sound device may already be closed.
        }
    }

    /** Pure clock-driven state machine for the trigger -> capture -> hold -> stop lifecycle. */
    static final class Timeline {
        enum State { IDLE, ARMED, HOLDING, CLOSING }

        private final LongSupplier clock;
        private final long holdMillis;
        private State state = State.IDLE;
        private long startedAtMillis;

        Timeline(LongSupplier clock, long holdMillis) {
            this.clock = Objects.requireNonNull(clock, "clock");
            this.holdMillis = holdMillis;
        }

        boolean active() {
            return state != State.IDLE;
        }

        State state() {
            return state;
        }

        boolean trigger() {
            if (state != State.IDLE) return false;
            state = State.ARMED;
            startedAtMillis = clock.getAsLong();
            return true;
        }

        boolean awaitingCapture() {
            return state == State.ARMED;
        }

        void captured() {
            if (state != State.ARMED) return;
            state = State.HOLDING;
            startedAtMillis = clock.getAsLong();
        }

        boolean shouldStopNow() {
            if (state != State.HOLDING && state != State.ARMED) return false;
            if (clock.getAsLong() - startedAtMillis < holdMillis) return false;
            state = State.CLOSING;
            return true;
        }
    }
}
