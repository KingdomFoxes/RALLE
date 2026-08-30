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
import org.kingdomfoxes.ralle.api.hud.RalleHudElements;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.PlacementPolicy;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatLayoutEditorScreen extends BaseOwoScreen<FlowLayout> {
    static final int GRID_SIZE = 10;
    private static final int SNAP_DISTANCE = 4;
    private static final int RESIZE_MARGIN = 6;
    static final int EDITOR_WINDOW_MARGIN = 3;
    static final int EDITOR_OUTER_FRAME_THICKNESS = 2;
    static final int RESIZE_CORNER_SIZE = 6;
    private static final int CHAT_FILL = 0x88243A55;
    private static final int CHAT_OUTLINE = 0xFFE5B94C;
    static final int EDITOR_OUTER_FRAME = CHAT_OUTLINE;
    static final int EDITOR_INNER_FRAME = RalleTheme.DARK_GOLD_ARGB;
    static final int EDITOR_LABEL = EDITOR_OUTER_FRAME;
    private static final int OTHER_FILL = 0x55263A5A;
    private static final int OTHER_OUTLINE = 0xFF66738A;
    private static final int GRID_LINE = 0x404D6380;
    private static final int GRID_MAJOR_LINE = 0x606A82A4;
    private static final int ALIGNMENT_LINE = 0xFFFF4040;

    private final Screen parent;
    private final ChatLayoutService chatLayout;
    private final List<String> editableElementIds;
    private final List<String> previewElementIds;
    private final boolean editAll;
    private final Map<String, Rectangle> elementBounds = new LinkedHashMap<>();
    private final Map<String, Rectangle> initialElementBounds = new LinkedHashMap<>();
    private final Map<String, Boolean> initiallyCustomized = new LinkedHashMap<>();
    private String selectedElementId;
    private FlowLayout controlsPanel;
    private CheckboxComponent freeMoveCheckbox;
    private CheckboxComponent snapGridCheckbox;
    private Rectangle bounds;
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
        this(parent, chatLayout, ChatLayoutService.CHAT_ELEMENT_ID);
    }

    ChatLayoutEditorScreen(Screen parent, ChatLayoutService chatLayout, String selectedElementId) {
        this(parent, chatLayout, List.of(selectedElementId), allPreviewElementIds(), false);
    }

    private ChatLayoutEditorScreen(
            Screen parent,
            ChatLayoutService chatLayout,
            List<String> editableElementIds,
            List<String> previewElementIds,
            boolean editAll
    ) {
        this.parent = parent;
        this.chatLayout = chatLayout;
        this.editableElementIds = List.copyOf(editableElementIds);
        this.previewElementIds = List.copyOf(previewElementIds);
        this.editAll = editAll;
        this.showAll = editAll;
        this.selectedElementId = this.editableElementIds.getFirst();
    }

    public static ChatLayoutEditorScreen forAllEnabled(
            Screen parent,
            ChatLayoutService chatLayout,
            SettingsRegistry settings
    ) {
        boolean raidLfgEnabled = settings.setting("raid-lfg-enabled", BooleanSetting.class).value();
        return new ChatLayoutEditorScreen(
                parent,
                chatLayout,
                enabledEditableElementIds(raidLfgEnabled),
                enabledPreviewElementIds(raidLfgEnabled),
                true
        );
    }

    static List<String> enabledEditableElementIds(boolean raidLfgEnabled) {
        return raidLfgEnabled
                ? List.of(RalleHudElements.CHAT, RalleHudElements.LFG_NOTIFICATIONS)
                : List.of(RalleHudElements.CHAT);
    }

    static List<String> enabledPreviewElementIds(boolean raidLfgEnabled) {
        return raidLfgEnabled
                ? allPreviewElementIds()
                : List.of(RalleHudElements.CHAT);
    }

    private static List<String> allPreviewElementIds() {
        return List.of(
                RalleHudElements.CHAT,
                RalleHudElements.LFG_NOTIFICATIONS,
                RalleHudElements.LFG_ACTION_BAR
        );
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        elementBounds.clear();
        initialElementBounds.clear();
        initiallyCustomized.clear();
        for (var elementId : editableElementIds) {
            var elementBounds = chatLayout.editorBounds(elementId, width, height);
            this.elementBounds.put(elementId, elementBounds);
            initialElementBounds.put(elementId, elementBounds);
            initiallyCustomized.put(elementId, chatLayout.hasCustomEditorBounds(elementId));
        }
        bounds = elementBounds.get(selectedElementId);

        root.surface(Surface.BLANK)
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
        checkbox(movementColumn, "show-all", editAll, checked -> showAll = checked);
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
        for (var elementId : editableElementIds) {
            Rectangle restored;
            if (initiallyCustomized.getOrDefault(elementId, false)) {
                restored = initialElementBounds.get(elementId);
                chatLayout.saveEditorBounds(elementId, restored, width, height);
            } else {
                restored = chatLayout.resetEditorBounds(elementId, width, height);
            }
            elementBounds.put(elementId, restored);
        }
        bounds = elementBounds.get(selectedElementId);
        dragMode = DragMode.NONE;
        clearAlignmentGuides();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (showGrid) renderGrid(graphics);
        if (showAll) renderOtherElements(graphics);

        if (bounds != null) {
            var interactionBounds = editorInteractionBounds(bounds, width, height);
            graphics.fill(
                    interactionBounds.x(),
                    interactionBounds.y(),
                    interactionBounds.right(),
                    interactionBounds.bottom(),
                    CHAT_FILL
            );
            renderEditorFrame(
                    graphics,
                    interactionBounds,
                    chatLayout.placementPolicy(selectedElementId)
            );
            renderElementName(
                    graphics,
                    selectedElementId,
                    bounds,
                    EDITOR_LABEL,
                    showPositionInfo ? -6 : 0
            );
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
                        centeredTextY(bounds, 6),
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
        for (var elementId : previewElementIds) {
            if (elementId.equals(selectedElementId)) continue;
            var otherBounds = elementBounds.containsKey(elementId)
                    ? elementBounds.get(elementId)
                    : chatLayout.editorBounds(elementId, width, height);
            graphics.fill(otherBounds.x(), otherBounds.y(), otherBounds.right(), otherBounds.bottom(), OTHER_FILL);
            graphics.renderOutline(
                    otherBounds.x(), otherBounds.y(), otherBounds.width(), otherBounds.height(), OTHER_OUTLINE
            );
            renderElementName(graphics, elementId, otherBounds, 0xFFA9B0BE, 0);
        }
    }

    private void renderElementName(
            GuiGraphics graphics,
            String elementId,
            Rectangle elementBounds,
            int color,
            int verticalOffset
    ) {
        graphics.drawCenteredString(
                font,
                RalleTypography.body(Component.literal(elementId)),
                elementBounds.x() + elementBounds.width() / 2,
                centeredTextY(elementBounds, verticalOffset),
                color
        );
    }

    static int centeredTextY(Rectangle elementBounds, int verticalOffset) {
        return elementBounds.y() + Math.max(4, elementBounds.height() / 2 - 4) + verticalOffset;
    }

    private void renderAlignmentGuides(GuiGraphics graphics) {
        if (alignmentGuideX != null) {
            graphics.fill(alignmentGuideX, 0, alignmentGuideX + 1, height, ALIGNMENT_LINE);
        }
        if (alignmentGuideY != null) {
            graphics.fill(0, alignmentGuideY, width, alignmentGuideY + 1, ALIGNMENT_LINE);
        }
    }

    /** Draws the shared nested frame, adding tapered corners only when the element can resize. */
    private void renderEditorFrame(
            GuiGraphics graphics,
            Rectangle interactionBounds,
            PlacementPolicy placementPolicy
    ) {
        for (var segment : connectedFrameSegments(interactionBounds, EDITOR_OUTER_FRAME_THICKNESS)) {
            graphics.fill(segment.x(), segment.y(), segment.right(), segment.bottom(), EDITOR_OUTER_FRAME);
        }
        var insetFrame = nestedInsetFrame(interactionBounds, EDITOR_WINDOW_MARGIN);
        graphics.renderOutline(
                insetFrame.x(),
                insetFrame.y(),
                insetFrame.width(),
                insetFrame.height(),
                EDITOR_INNER_FRAME
        );
        for (var strip : cornerAccentStrips(insetFrame, placementPolicy)) {
            graphics.fill(strip.x(), strip.y(), strip.right(), strip.bottom(), EDITOR_INNER_FRAME);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (isControlsAt(event.x(), event.y())) return super.mouseClicked(event, doubled);
        if (event.button() != 0 || !selectEditableElementAt(event.x(), event.y())) {
            return super.mouseClicked(event, doubled);
        }

        boolean resizable = isSelectedElementResizable();
        horizontalResizeEdge = resizable ? edgeAt(event.x(), bounds.x(), bounds.right(), RESIZE_MARGIN) : AxisEdge.NONE;
        verticalResizeEdge = resizable ? edgeAt(event.y(), bounds.y(), bounds.bottom(), RESIZE_MARGIN) : AxisEdge.NONE;
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

    private boolean selectEditableElementAt(double pointerX, double pointerY) {
        if (editAll && showAll) {
            for (var elementId : editableElementIds) {
                if (elementId.equals(selectedElementId)) continue;
                var candidate = elementBounds.get(elementId);
                if (candidate == null || !containsEditorPoint(candidate, pointerX, pointerY)) continue;
                selectedElementId = elementId;
                bounds = candidate;
                return true;
            }
        }
        return bounds != null && containsEditorPoint(bounds, pointerX, pointerY);
    }

    private boolean containsEditorPoint(Rectangle candidate, double pointerX, double pointerY) {
        return editorInteractionBounds(candidate, width, height).contains(pointerX, pointerY);
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
        if (chatLayout.placementPolicy(selectedElementId) == PlacementPolicy.FIXED_SIDE_ANCHORED) {
            int sideX = desiredX + bounds.width() / 2 < width / 2
                    ? Math.min(8, Math.max(0, width - bounds.width()))
                    : Math.max(0, width - bounds.width() - Math.min(8, Math.max(0, (width - bounds.width()) / 2)));
            var ySnap = snapAxis(desiredY, bounds.height(), height, snapToGrid, alignmentGuides);
            setBounds(new Rectangle(sideX, ySnap.start(), bounds.width(), bounds.height())
                    .clampTo(width, height, bounds.width(), bounds.height()));
            alignmentGuideX = null;
            alignmentGuideY = featureMatches(bounds.y(), bounds.height(), ySnap.guide()) ? ySnap.guide() : null;
            return;
        }
        var xSnap = snapAxis(
                desiredX, bounds.width(), width,
                snapToGrid, alignmentGuides
        );
        var ySnap = snapAxis(
                desiredY, bounds.height(), height,
                snapToGrid, alignmentGuides
        );
        setBounds(new Rectangle(xSnap.start(), ySnap.start(), bounds.width(), bounds.height())
                .clampTo(width, height,
                        chatLayout.minimumWidth(selectedElementId), chatLayout.minimumHeight(selectedElementId)));
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

        setBounds(resize(
                bounds,
                horizontalResizeEdge,
                verticalResizeEdge,
                adjustedPointerX,
                adjustedPointerY,
                0,
                0,
                width,
                height,
                chatLayout.minimumWidth(selectedElementId),
                chatLayout.minimumHeight(selectedElementId)
        ));
        alignmentGuideX = resizeEdgeMatches(bounds, horizontalResizeEdge, xSnap) ? xSnap.guide() : null;
        alignmentGuideY = resizeEdgeMatches(bounds, verticalResizeEdge, ySnap) ? ySnap.guide() : null;
    }

    private static boolean resizeEdgeMatches(Rectangle bounds, AxisEdge edge, AxisSnap snap) {
        if (snap == null || snap.guide() == null) return false;
        return edge == AxisEdge.START && bounds.x() == snap.start()
                || edge == AxisEdge.END && bounds.right() == snap.start();
    }

    private void setBounds(Rectangle updatedBounds) {
        bounds = updatedBounds;
        elementBounds.put(selectedElementId, updatedBounds);
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
            chatLayout.saveEditorBounds(selectedElementId, bounds, width, height);
            dragMode = DragMode.NONE;
            clearAlignmentGuides();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (dragMode != DragMode.NONE && bounds != null) {
            chatLayout.saveEditorBounds(selectedElementId, bounds, width, height);
        }
        minecraft.setScreen(parent);
    }

    private boolean isControlsAt(double pointerX, double pointerY) {
        return controlsPanel != null && controlsPanel.isInBoundingBox(pointerX, pointerY);
    }

    private boolean isSelectedElementResizable() {
        return chatLayout.placementPolicy(selectedElementId) == PlacementPolicy.RESIZABLE_RECTANGLE;
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

    static Rectangle editorInteractionBounds(Rectangle bounds, int viewportWidth, int viewportHeight) {
        int left = Math.max(0, bounds.x() - EDITOR_WINDOW_MARGIN);
        int top = Math.max(0, bounds.y() - EDITOR_WINDOW_MARGIN);
        int right = Math.min(viewportWidth, bounds.right() + EDITOR_WINDOW_MARGIN);
        int bottom = Math.min(viewportHeight, bounds.bottom() + EDITOR_WINDOW_MARGIN);
        return new Rectangle(left, top, right - left, bottom - top);
    }

    static List<Rectangle> connectedFrameSegments(Rectangle frame, int requestedThickness) {
        int thickness = Math.min(Math.max(1, requestedThickness), Math.min(frame.width(), frame.height()));
        return List.of(
                new Rectangle(frame.x(), frame.y(), frame.width(), thickness),
                new Rectangle(frame.x(), frame.bottom() - thickness, frame.width(), thickness),
                new Rectangle(frame.x(), frame.y(), thickness, frame.height()),
                new Rectangle(frame.right() - thickness, frame.y(), thickness, frame.height())
        );
    }

    static Rectangle nestedInsetFrame(Rectangle outerFrame, int requestedInset) {
        int inset = Math.max(0, requestedInset);
        int horizontalInset = Math.min(inset, Math.max(0, (outerFrame.width() - 1) / 2));
        int verticalInset = Math.min(inset, Math.max(0, (outerFrame.height() - 1) / 2));
        return new Rectangle(
                outerFrame.x() + horizontalInset,
                outerFrame.y() + verticalInset,
                outerFrame.width() - horizontalInset * 2,
                outerFrame.height() - verticalInset * 2
        );
    }

    static List<Rectangle> cornerAccentStrips(Rectangle frame, int requestedSize) {
        int size = Math.min(Math.max(1, requestedSize), Math.min(frame.width(), frame.height()));
        var strips = new ArrayList<Rectangle>((size - 1) * 4);
        for (int depth = 1; depth < size; depth++) {
            int width = size - depth + 1;
            int top = frame.y() + depth;
            int bottom = frame.bottom() - depth - 1;
            strips.add(new Rectangle(frame.x(), top, width, 1));
            strips.add(new Rectangle(frame.right() - width, top, width, 1));
            strips.add(new Rectangle(frame.x(), bottom, width, 1));
            strips.add(new Rectangle(frame.right() - width, bottom, width, 1));
        }
        return List.copyOf(strips);
    }

    static List<Rectangle> cornerAccentStrips(Rectangle frame, PlacementPolicy placementPolicy) {
        return placementPolicy == PlacementPolicy.RESIZABLE_RECTANGLE
                ? cornerAccentStrips(frame, RESIZE_CORNER_SIZE)
                : List.of();
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
            int viewportHeight,
            int requestedMinimumWidth,
            int requestedMinimumHeight
    ) {
        var minimumWidth = Math.min(requestedMinimumWidth, viewportWidth);
        var minimumHeight = Math.min(requestedMinimumHeight, viewportHeight);
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
