package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.jetbrains.annotations.NotNull;

public final class ChatLayoutEditorScreen extends BaseOwoScreen<FlowLayout> {
    static final int GRID_SIZE = 10;
    private static final int SNAP_DISTANCE = 4;
    private static final int RESIZE_MARGIN = 6;
    private static final int CHAT_FILL = 0x88243A55;
    private static final int CHAT_OUTLINE = 0xFFE5B94C;
    private static final int OTHER_FILL = 0x55263A5A;
    private static final int OTHER_OUTLINE = 0xFF66738A;
    private static final int GRID_LINE = 0x404D6380;
    private static final int GRID_MAJOR_LINE = 0x606A82A4;
    private static final int ALIGNMENT_LINE = 0xFFFF4040;

    private final Screen parent;
    private final ChatLayoutService chatLayout;
    private FlowLayout controlsPanel;
    private CheckboxComponent freeMoveCheckbox;
    private CheckboxComponent snapGridCheckbox;
    private Rectangle bounds;
    private Rectangle initialBounds;
    private boolean initialHadCustomBounds;
    private DragMode dragMode = DragMode.NONE;
    private AxisEdge horizontalResizeEdge = AxisEdge.NONE;
    private AxisEdge verticalResizeEdge = AxisEdge.NONE;
    private int pointerOffsetX;
    private int pointerOffsetY;
    private Integer alignmentGuideX;
    private Integer alignmentGuideY;
    private boolean updatingMovementCheckboxes;
    private boolean snapToGrid;
    private boolean alignmentGuides;
    private boolean showAll;
    private boolean showGrid;
    private boolean showPositionInfo;

    ChatLayoutEditorScreen(Screen parent, ChatLayoutService chatLayout) {
        this.parent = parent;
        this.chatLayout = chatLayout;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        bounds = chatLayout.editorBounds(width, height);
        initialBounds = bounds;
        initialHadCustomBounds = chatLayout.hasCustomEditorBounds();

        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP);
        root.child(UIComponents.spacer());

        controlsPanel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        controlsPanel.gap(4).padding(Insets.of(8)).surface(RalleSurfaces.FRAMED_NAVY);

        var toggles = UIContainers.horizontalFlow(Sizing.fixed(300), Sizing.content());
        var movementColumn = checkboxColumn(84);
        var gridColumn = checkboxColumn(94);
        var displayColumn = checkboxColumn(122);

        freeMoveCheckbox = checkbox(movementColumn, "free-move", true, checked -> setSnapToGrid(!checked));
        snapGridCheckbox = checkbox(gridColumn, "snap-grid", false, this::setSnapToGrid);
        checkbox(displayColumn, "alignment-guides", false, checked -> {
            alignmentGuides = checked;
            if (!checked) clearAlignmentGuides();
        });
        checkbox(movementColumn, "show-all", false, checked -> showAll = checked);
        checkbox(gridColumn, "show-grid", false, checked -> showGrid = checked);
        checkbox(displayColumn, "position-info", false, checked -> showPositionInfo = checked);
        toggles.child(movementColumn).child(gridColumn).child(displayColumn);
        controlsPanel.child(toggles);

        var buttons = UIContainers.horizontalFlow(Sizing.fixed(300), Sizing.content());
        buttons.gap(8);
        var resetButton = UIComponents.button(
                RalleTypography.body(Component.translatable("ralle.chat-layout.reset")),
                button -> resetSessionChanges()
        );
        resetButton.horizontalSizing(Sizing.fixed(146));
        resetButton.renderer(RalleButtonRenderers.neutral());

        var doneButton = UIComponents.button(RalleTypography.body(Component.translatable("gui.done")), button -> onClose());
        doneButton.horizontalSizing(Sizing.fixed(146));
        doneButton.renderer(RalleButtonRenderers.primary());
        buttons.child(resetButton).child(doneButton);
        controlsPanel.child(buttons);
        root.child(controlsPanel);
    }

    private FlowLayout checkboxColumn(int width) {
        return UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content()).gap(4);
    }

    private CheckboxComponent checkbox(
            FlowLayout parent,
            String id,
            boolean checked,
            java.util.function.Consumer<Boolean> changed
    ) {
        var checkbox = UIComponents.checkbox(RalleTypography.body(Component.translatable("ralle.chat-layout." + id)));
        checkbox.checked(checked);
        checkbox.tooltip(RalleTypography.body(Component.translatable("ralle.chat-layout." + id + ".description")));
        checkbox.onChanged(changed);
        parent.child(checkbox);
        return checkbox;
    }

    private void setSnapToGrid(boolean enabled) {
        if (updatingMovementCheckboxes) return;
        snapToGrid = enabled;
        updatingMovementCheckboxes = true;
        freeMoveCheckbox.checked(!enabled);
        snapGridCheckbox.checked(enabled);
        updatingMovementCheckboxes = false;
    }

    private void resetSessionChanges() {
        bounds = initialBounds;
        if (initialHadCustomBounds) {
            chatLayout.saveEditorBounds(bounds, width, height);
        } else {
            chatLayout.resetEditorBounds(width, height);
        }
        dragMode = DragMode.NONE;
        clearAlignmentGuides();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (showGrid) renderGrid(graphics);
        if (showAll) renderOtherElements(graphics);

        if (bounds != null) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), CHAT_FILL);
            graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), CHAT_OUTLINE);
            if (showPositionInfo) {
                graphics.drawCenteredString(
                        font,
                        RalleTypography.body(Component.translatable(
                                "ralle.chat-layout.bounds",
                                bounds.x(),
                                bounds.y(),
                                bounds.width(),
                                bounds.height()
                        )),
                        bounds.x() + bounds.width() / 2,
                        bounds.y() + Math.max(4, bounds.height() / 2 - 4),
                        0xFFFFFFFF
                );
            }
        }

        if (alignmentGuides) renderAlignmentGuides(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGrid(GuiGraphics graphics) {
        for (int x = 0; x < width; x += GRID_SIZE) {
            var color = x % (GRID_SIZE * 5) == 0 ? GRID_MAJOR_LINE : GRID_LINE;
            graphics.fill(x, 0, x + 1, height, color);
        }
        for (int y = 0; y < height; y += GRID_SIZE) {
            var color = y % (GRID_SIZE * 5) == 0 ? GRID_MAJOR_LINE : GRID_LINE;
            graphics.fill(0, y, width, y + 1, color);
        }
    }

    private void renderOtherElements(GuiGraphics graphics) {
        for (var entry : chatLayout.otherEditorBounds(width, height).entrySet()) {
            var otherBounds = entry.getValue();
            graphics.fill(otherBounds.x(), otherBounds.y(), otherBounds.right(), otherBounds.bottom(), OTHER_FILL);
            graphics.renderOutline(
                    otherBounds.x(), otherBounds.y(), otherBounds.width(), otherBounds.height(), OTHER_OUTLINE
            );
            graphics.drawCenteredString(
                    font,
                    RalleTypography.body(Component.literal(entry.getKey())),
                    otherBounds.x() + otherBounds.width() / 2,
                    otherBounds.y() + Math.max(4, otherBounds.height() / 2 - 4),
                    0xFFA9B0BE
            );
        }
    }

    private void renderAlignmentGuides(GuiGraphics graphics) {
        if (alignmentGuideX != null) {
            graphics.fill(alignmentGuideX, 0, alignmentGuideX + 1, height, ALIGNMENT_LINE);
        }
        if (alignmentGuideY != null) {
            graphics.fill(0, alignmentGuideY, width, alignmentGuideY + 1, ALIGNMENT_LINE);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (isControlsAt(event.x(), event.y())) return super.mouseClicked(event, doubled);
        if (event.button() != 0 || bounds == null || !bounds.contains(event.x(), event.y())) {
            return super.mouseClicked(event, doubled);
        }

        horizontalResizeEdge = edgeAt(event.x(), bounds.x(), bounds.right(), RESIZE_MARGIN);
        verticalResizeEdge = edgeAt(event.y(), bounds.y(), bounds.bottom(), RESIZE_MARGIN);
        if (horizontalResizeEdge != AxisEdge.NONE || verticalResizeEdge != AxisEdge.NONE) {
            dragMode = DragMode.RESIZE;
            pointerOffsetX = pointerOffset(event.x(), bounds.x(), bounds.right(), horizontalResizeEdge);
            pointerOffsetY = pointerOffset(event.y(), bounds.y(), bounds.bottom(), verticalResizeEdge);
        } else {
            dragMode = DragMode.MOVE;
            pointerOffsetX = (int) event.x() - bounds.x();
            pointerOffsetY = (int) event.y() - bounds.y();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragMode == DragMode.NONE || bounds == null) return super.mouseDragged(event, deltaX, deltaY);

        if (dragMode == DragMode.MOVE) {
            moveTo((int) event.x() - pointerOffsetX, (int) event.y() - pointerOffsetY);
        } else {
            resizeTo((int) event.x(), (int) event.y());
        }
        return true;
    }

    private void moveTo(int desiredX, int desiredY) {
        var xSnap = snapAxis(
                desiredX, bounds.width(), width,
                snapToGrid, alignmentGuides
        );
        var ySnap = snapAxis(
                desiredY, bounds.height(), height,
                snapToGrid, alignmentGuides
        );
        bounds = new Rectangle(xSnap.start(), ySnap.start(), bounds.width(), bounds.height())
                .clampTo(width, height, ChatLayoutService.MINIMUM_WIDTH, ChatLayoutService.MINIMUM_HEIGHT);
        alignmentGuideX = featureMatches(bounds.x(), bounds.width(), xSnap.guide()) ? xSnap.guide() : null;
        alignmentGuideY = featureMatches(bounds.y(), bounds.height(), ySnap.guide()) ? ySnap.guide() : null;
    }

    private void resizeTo(int pointerX, int pointerY) {
        AxisSnap xSnap = null;
        AxisSnap ySnap = null;
        var adjustedPointerX = pointerX;
        var adjustedPointerY = pointerY;

        if (horizontalResizeEdge != AxisEdge.NONE) {
            var edgeCoordinate = horizontalResizeEdge == AxisEdge.START
                    ? pointerX - pointerOffsetX
                    : pointerX + pointerOffsetX;
            xSnap = snapAxis(
                    edgeCoordinate, 0, width,
                    snapToGrid, alignmentGuides
            );
            adjustedPointerX = xSnap.start();
        }
        if (verticalResizeEdge != AxisEdge.NONE) {
            var edgeCoordinate = verticalResizeEdge == AxisEdge.START
                    ? pointerY - pointerOffsetY
                    : pointerY + pointerOffsetY;
            ySnap = snapAxis(
                    edgeCoordinate, 0, height,
                    snapToGrid, alignmentGuides
            );
            adjustedPointerY = ySnap.start();
        }

        bounds = resize(
                bounds,
                horizontalResizeEdge,
                verticalResizeEdge,
                adjustedPointerX,
                adjustedPointerY,
                0,
                0,
                width,
                height
        );
        alignmentGuideX = resizeEdgeMatches(bounds, horizontalResizeEdge, xSnap) ? xSnap.guide() : null;
        alignmentGuideY = resizeEdgeMatches(bounds, verticalResizeEdge, ySnap) ? ySnap.guide() : null;
    }

    private static boolean resizeEdgeMatches(Rectangle bounds, AxisEdge edge, AxisSnap snap) {
        if (snap == null || snap.guide() == null) return false;
        return edge == AxisEdge.START && bounds.x() == snap.start()
                || edge == AxisEdge.END && bounds.right() == snap.start();
    }

    private static boolean featureMatches(int start, int size, Integer guide) {
        if (guide == null) return false;
        return start == guide || start + size / 2 == guide || start + size == guide;
    }

    static AxisSnap snapAxis(
            int desiredStart,
            int elementSize,
            int viewportSize,
            boolean snapToGrid,
            boolean alignmentGuides
    ) {
        var start = desiredStart;
        SnapCandidate best = null;
        var features = new int[]{start, start + elementSize / 2, start + elementSize};

        if (alignmentGuides) {
            var screenAnchors = new int[]{0, viewportSize / 2, viewportSize};
            for (var feature : features) {
                for (var anchor : screenAnchors) {
                    best = closer(best, new SnapCandidate(anchor - feature, anchor));
                }
            }
        }

        if (snapToGrid) {
            for (var feature : features) {
                var anchor = nearestGridLine(feature);
                best = closer(best, new SnapCandidate(anchor - feature, anchor));
            }
        }

        Integer guide = null;
        if (best != null && Math.abs(best.offset()) <= SNAP_DISTANCE) {
            start += best.offset();
            if (alignmentGuides) guide = best.guide();
        }

        var maximumStart = Math.max(0, viewportSize - elementSize);
        start = Math.clamp(start, 0, maximumStart);
        return new AxisSnap(start, guide);
    }

    private static SnapCandidate closer(SnapCandidate current, SnapCandidate candidate) {
        if (Math.abs(candidate.offset()) > SNAP_DISTANCE) return current;
        if (current == null || Math.abs(candidate.offset()) < Math.abs(current.offset())) return candidate;
        return current;
    }

    private static int nearestGridLine(int coordinate) {
        return Math.round(coordinate / (float) GRID_SIZE) * GRID_SIZE;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragMode != DragMode.NONE) {
            chatLayout.saveEditorBounds(bounds, width, height);
            dragMode = DragMode.NONE;
            clearAlignmentGuides();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (dragMode != DragMode.NONE && bounds != null) {
            chatLayout.saveEditorBounds(bounds, width, height);
        }
        minecraft.setScreen(parent);
    }

    private boolean isControlsAt(double pointerX, double pointerY) {
        return controlsPanel != null && controlsPanel.isInBoundingBox(pointerX, pointerY);
    }

    private void clearAlignmentGuides() {
        alignmentGuideX = null;
        alignmentGuideY = null;
    }

    static AxisEdge edgeAt(double pointer, int start, int end, int margin) {
        if (pointer - start < margin) return AxisEdge.START;
        if (end - pointer <= margin) return AxisEdge.END;
        return AxisEdge.NONE;
    }

    private static int pointerOffset(double pointer, int start, int end, AxisEdge edge) {
        return switch (edge) {
            case START -> (int) pointer - start;
            case END -> end - (int) pointer;
            case NONE -> 0;
        };
    }

    static Rectangle resize(
            Rectangle bounds,
            AxisEdge horizontalEdge,
            AxisEdge verticalEdge,
            int pointerX,
            int pointerY,
            int pointerOffsetX,
            int pointerOffsetY,
            int viewportWidth,
            int viewportHeight
    ) {
        var minimumWidth = Math.min(ChatLayoutService.MINIMUM_WIDTH, viewportWidth);
        var minimumHeight = Math.min(ChatLayoutService.MINIMUM_HEIGHT, viewportHeight);
        var left = bounds.x();
        var right = bounds.right();
        var top = bounds.y();
        var bottom = bounds.bottom();

        if (horizontalEdge == AxisEdge.START) {
            left = Math.clamp(pointerX - pointerOffsetX, 0, right - minimumWidth);
        } else if (horizontalEdge == AxisEdge.END) {
            right = Math.clamp(pointerX + pointerOffsetX, left + minimumWidth, viewportWidth);
        }

        if (verticalEdge == AxisEdge.START) {
            top = Math.clamp(pointerY - pointerOffsetY, 0, bottom - minimumHeight);
        } else if (verticalEdge == AxisEdge.END) {
            bottom = Math.clamp(pointerY + pointerOffsetY, top + minimumHeight, viewportHeight);
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }

    record AxisSnap(int start, Integer guide) {}

    private record SnapCandidate(int offset, int guide) {}

    enum AxisEdge {
        NONE,
        START,
        END
    }
}
