package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;

public final class ChatLayoutEditorScreen extends BaseUIModelScreen<FlowLayout> {
    private static final Identifier UI_MODEL = Identifier.fromNamespaceAndPath("ralle", "chat_layout");
    private static final int RESIZE_MARGIN = 6;
    private static final int CHAT_FILL = 0x88243A55;
    private static final int CHAT_OUTLINE = 0xFFE5B94C;

    private final Screen parent;
    private final ChatLayoutService chatLayout;
    private ButtonComponent resetButton;
    private ButtonComponent doneButton;
    private Rectangle bounds;
    private DragMode dragMode = DragMode.NONE;
    private AxisEdge horizontalResizeEdge = AxisEdge.NONE;
    private AxisEdge verticalResizeEdge = AxisEdge.NONE;
    private int pointerOffsetX;
    private int pointerOffsetY;

    ChatLayoutEditorScreen(Screen parent, ChatLayoutService chatLayout) {
        super(FlowLayout.class, UI_MODEL);
        this.parent = parent;
        this.chatLayout = chatLayout;
    }

    @Override
    protected void build(FlowLayout root) {
        bounds = chatLayout.editorBounds(width, height);
        resetButton = component(ButtonComponent.class, "reset-button");
        resetButton.onPress(button -> {
            bounds = chatLayout.resetEditorBounds(width, height);
            dragMode = DragMode.NONE;
        });
        doneButton = component(ButtonComponent.class, "done-button");
        doneButton.onPress(button -> onClose());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (bounds != null) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), CHAT_FILL);
            graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), CHAT_OUTLINE);
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "ralle.chat-layout.bounds",
                            bounds.x(),
                            bounds.y(),
                            bounds.width(),
                            bounds.height()
                    ),
                    bounds.x() + bounds.width() / 2,
                    bounds.y() + Math.max(4, bounds.height() / 2 - 4),
                    0xFFFFFFFF
            );
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (isEditorButtonAt(event.x(), event.y())) return super.mouseClicked(event, doubled);
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
            bounds = new Rectangle(
                    (int) event.x() - pointerOffsetX,
                    (int) event.y() - pointerOffsetY,
                    bounds.width(),
                    bounds.height()
            ).clampTo(width, height, ChatLayoutService.MINIMUM_WIDTH, ChatLayoutService.MINIMUM_HEIGHT);
        } else {
            bounds = resize(
                    bounds,
                    horizontalResizeEdge,
                    verticalResizeEdge,
                    (int) event.x(),
                    (int) event.y(),
                    pointerOffsetX,
                    pointerOffsetY,
                    width,
                    height
            );
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragMode != DragMode.NONE) {
            chatLayout.saveEditorBounds(bounds, width, height);
            dragMode = DragMode.NONE;
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

    private boolean isEditorButtonAt(double pointerX, double pointerY) {
        return resetButton != null && resetButton.isInBoundingBox(pointerX, pointerY)
                || doneButton != null && doneButton.isInBoundingBox(pointerX, pointerY);
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

    enum AxisEdge {
        NONE,
        START,
        END
    }
}
