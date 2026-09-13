package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Compact shared confirmation, mounted inside the browser or over gameplay for keybinds. */
public final class WynncraftPartyQueuePrompt extends BaseOwoScreen<FlowLayout> {
    public enum Choice { PARTY, SOLO, CANCEL }

    private final List<String> members;
    private final CompletableFuture<Choice> result;
    private final LfgSelectorWheelScreen wheel;

    private WynncraftPartyQueuePrompt(List<String> members, CompletableFuture<Choice> result,
                                     LfgSelectorWheelScreen wheel) {
        this.members = members;
        this.result = result;
        this.wheel = wheel;
    }

    public static CompletableFuture<Choice> show(List<String> members) {
        var result = new CompletableFuture<Choice>();
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RaidLfgScreen browser) browser.showPartyQueuePrompt(members, result);
        else {
            var wheel = minecraft.screen instanceof LfgSelectorWheelScreen selector ? selector : null;
            if (wheel != null) wheel.suspendForPartyPrompt();
            minecraft.setScreen(new WynncraftPartyQueuePrompt(members, result, wheel));
        }
        return result;
    }

    static FlowLayout content(List<String> members, Consumer<Choice> choose) {
        var content = UIContainers.verticalFlow(Sizing.fixed(300), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.lfg.party-queue.title")))
                .color(RalleTheme.ACCENT).maxWidth(276));
        content.child(UIComponents.label(RalleTheme.ui(Component.translatable("ralle.lfg.party-queue.body",
                String.join(", ", members)))).maxWidth(276));
        if (members.size() > 3) content.child(UIComponents.label(RalleTheme.ui(
                Component.translatable("ralle.lfg.party-queue.too-large"))).color(RalleTheme.MUTED).maxWidth(276));
        var party = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.lfg.party-queue.yes")),
                ignored -> choose.accept(Choice.PARTY));
        party.horizontalSizing(Sizing.fill(100));
        party.renderer(RalleButtonRenderers.primary());
        party.active = members.size() <= 3;
        var solo = UIComponents.button(RalleTheme.ui(Component.translatable("ralle.lfg.party-queue.solo")),
                ignored -> choose.accept(Choice.SOLO));
        solo.horizontalSizing(Sizing.fill(100));
        solo.renderer(RalleButtonRenderers.neutral());
        var cancel = UIComponents.button(RalleTheme.ui(Component.translatable("gui.cancel")),
                ignored -> choose.accept(Choice.CANCEL));
        cancel.horizontalSizing(Sizing.fill(100));
        cancel.renderer(RalleButtonRenderers.destructive());
        return content.child(party).child(solo).child(cancel);
    }

    @Override protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override protected void build(FlowLayout root) {
        root.child(new RalleModalOverlay<>(content(members, this::choose)));
    }

    private void choose(Choice choice) {
        // Complete before removed() can interpret changing the screen as cancellation.
        var completion = result;
        chosen = true;
        if (wheel != null && choice != Choice.CANCEL) {
            wheel.resumeFromPartyPrompt();
            Minecraft.getInstance().setScreen(wheel);
        } else {
            if (wheel != null) wheel.abandonPartyPrompt();
            Minecraft.getInstance().setScreen(null);
        }
        completion.complete(choice);
    }

    private boolean chosen;
    @Override public void onClose() { choose(Choice.CANCEL); }
    @Override public void removed() {
        if (!chosen) {
            if (wheel != null) wheel.abandonPartyPrompt();
            result.complete(Choice.CANCEL);
        }
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
