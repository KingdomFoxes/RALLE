package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Reusable RALLE presentation for the local-only alliance requirement preview. */
final class AllianceRequirementPreviewModal {
    private static final int MODAL_WIDTH = 440;
    private static final int CONTENT_WIDTH = MODAL_WIDTH - 24;
    private static final int ACTION_WIDTH = 184;

    private final AllianceRequirementPreview preview;
    private final OverlayContainer<FlowLayout> overlay;
    private final FlowLayout rows;
    private final LabelComponent readiness;
    private final ButtonComponent close;
    private final List<ButtonComponent> focusOrder = new ArrayList<>();
    private long renderedVersion = Long.MIN_VALUE;
    private long renderedLoadingFrame = Long.MIN_VALUE;

    AllianceRequirementPreviewModal(List<AllianceRequirementPreview.Guild> guilds,
                                    LongSupplier nanoTime) {
        this.preview = new AllianceRequirementPreview(guilds, nanoTime);

        var content = UIContainers.verticalFlow(Sizing.fixed(MODAL_WIDTH), Sizing.content());
        content.gap(8).padding(Insets.of(12)).surface(RalleSurfaces.FRAMED_NAVY);
        content.child(UIComponents.label(RalleTheme.ui(Component.literal("ALLIANCE REQUIRED")))
                .color(RalleTheme.ACCENT));
        content.child(UIComponents.label(RalleTheme.ui(Component.literal(
                        "Before you can join, your guild must ally with:")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.TEXT)
                .maxWidth(CONTENT_WIDTH));

        rows = UIContainers.verticalFlow(Sizing.fixed(CONTENT_WIDTH - 8), Sizing.content());
        rows.gap(5).padding(Insets.right(4));
        var scrollingRows = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fixed(132), rows);
        scrollingRows.scrollbarThiccness(4).scrollStep(28);
        content.child(scrollingRows);

        readiness = UIComponents.label(RalleTheme.ui(Component.literal("Alliance requests pending")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.MUTED);
        content.child(readiness);
        content.child(UIComponents.label(RalleTheme.ui(Component.literal(
                        "Preview only — no command or API request will be sent.")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.MUTED)
                .maxWidth(CONTENT_WIDTH));

        close = UIComponents.button(
                RalleTheme.ui(Component.literal("Close")), ignored -> close());
        close.sizing(Sizing.fill(100), Sizing.fixed(20));
        close.renderer(RalleButtonRenderers.neutral());
        close.tooltip(RalleTheme.ui(Component.literal("Close only this preview dialog")));
        content.child(close);

        overlay = UIContainers.overlay(content).closeOnClick(false);
        refresh();
    }

    OverlayContainer<FlowLayout> component() {
        return overlay;
    }

    boolean mounted() {
        return overlay.hasParent();
    }

    void tick() {
        preview.tick();
        long loadingFrame = preview.sending() ? preview.loadingFrame() : -1;
        if (renderedVersion != preview.version() || renderedLoadingFrame != loadingFrame) refresh();
    }

    void close() {
        overlay.remove();
    }

    boolean cycleFocus(boolean backwards) {
        var root = overlay.root();
        var focus = root == null ? null : root.focusHandler();
        if (focus == null || focusOrder.isEmpty()) return false;
        int current = focusOrder.indexOf(focus.focused());
        int next = current < 0
                ? (backwards ? focusOrder.size() - 1 : 0)
                : Math.floorMod(current + (backwards ? -1 : 1), focusOrder.size());
        focus.focus(focusOrder.get(next), io.wispforest.owo.ui.core.UIComponent.FocusSource.KEYBOARD_CYCLE);
        return true;
    }

    void focusFirst() {
        var root = overlay.root();
        var focus = root == null ? null : root.focusHandler();
        if (focus != null && !focusOrder.isEmpty()) {
            focus.focus(focusOrder.getFirst(), io.wispforest.owo.ui.core.UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
    }

    private void refresh() {
        renderedVersion = preview.version();
        renderedLoadingFrame = preview.sending() ? preview.loadingFrame() : -1;
        rows.clearChildren();
        focusOrder.clear();
        for (var row : preview.rows()) rows.child(guildRow(row));
        focusOrder.add(close);
        readiness.text(RalleTheme.ui(Component.literal(
                preview.readyToJoin() ? "Ready to join" : "Alliance requests pending")));
        readiness.color(preview.readyToJoin() ? RalleTheme.POSITIVE : RalleTheme.MUTED);
    }

    private FlowLayout guildRow(AllianceRequirementPreview.Row row) {
        var component = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        component.gap(6).padding(Insets.of(7)).surface(RalleSurfaces.NAVY_ROW)
                .verticalAlignment(VerticalAlignment.CENTER);

        var identity = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        identity.gap(2).horizontalAlignment(HorizontalAlignment.LEFT);
        identity.child(UIComponents.label(RalleTheme.ui(Component.literal(row.guild().name())))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.TEXT)
                .maxWidth(CONTENT_WIDTH - ACTION_WIDTH - 30));
        identity.child(UIComponents.label(RalleTheme.ui(Component.literal("[" + row.guild().tag() + "]")))
                .lineHeight(RalleTheme.BODY_LINE_HEIGHT)
                .color(RalleTheme.MUTED)
                .maxWidth(CONTENT_WIDTH - ACTION_WIDTH - 30));
        component.child(identity);
        component.child(actions(row));
        return component;
    }

    private FlowLayout actions(AllianceRequirementPreview.Row row) {
        var actions = UIContainers.verticalFlow(Sizing.fixed(ACTION_WIDTH), Sizing.content());
        actions.gap(3);
        switch (row.state()) {
            case REQUESTABLE -> actions.child(sendButton(row.guild()));
            case SENDING -> actions.child(statusButton(
                    "Sending… " + preview.loadingIndicator(row.guild().id()), false));
            case WAITING -> {
                actions.child(statusButton("Waiting for acceptance…", false));
                var accept = UIComponents.button(
                        RalleTheme.ui(Component.literal("Simulate acceptance")),
                        ignored -> {
                            if (preview.simulateAcceptance(row.guild().id())) {
                                refresh();
                                focusFirst();
                            }
                        });
                accept.sizing(Sizing.fill(100), Sizing.fixed(20));
                accept.renderer(RalleButtonRenderers.neutral());
                accept.tooltip(RalleTheme.ui(Component.literal(
                        "Preview only — simulate this guild accepting the request")));
                actions.child(accept);
                focusOrder.add(accept);
            }
            case ALLIED -> actions.child(statusButton("Allied", true));
        }
        return actions;
    }

    private ButtonComponent sendButton(AllianceRequirementPreview.Guild guild) {
        var send = UIComponents.button(RalleTheme.ui(Component.literal("Send ally request")), ignored -> {
            if (preview.request(guild.id())) {
                refresh();
                focusFirst();
            }
        });
        send.sizing(Sizing.fill(100), Sizing.fixed(20));
        send.renderer(RalleButtonRenderers.primary());
        send.tooltip(RalleTheme.ui(Component.literal(
                "Preview only — sends no command or API request")));
        focusOrder.add(send);
        return send;
    }

    private static ButtonComponent statusButton(String label, boolean positive) {
        var status = UIComponents.button(RalleTheme.ui(Component.literal(label)), ignored -> {});
        status.sizing(Sizing.fill(100), Sizing.fixed(20));
        status.renderer(positive
                ? RalleButtonRenderers.positiveStatus()
                : RalleButtonRenderers.neutral());
        status.active = false;
        status.tooltip(RalleTheme.ui(Component.literal(positive
                ? "Preview alliance request accepted"
                : "This preview state cannot be clicked")));
        return status;
    }
}
