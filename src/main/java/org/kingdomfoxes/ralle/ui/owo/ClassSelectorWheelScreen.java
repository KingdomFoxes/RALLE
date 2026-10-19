package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.kingdomfoxes.ralle.classes.ClassSelectorBindings;
import org.kingdomfoxes.ralle.classes.ClassShortcut;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Class presentation uses the same faces, stable hit regions and reversible motion as Create/Kick. */
public final class ClassSelectorWheelScreen extends Screen {
    private final ClassSelectorBindings controller;
    private final boolean bars;
    private final SelectorWheelModel model = new SelectorWheelModel();
    private final List<ClassSelectorBindings.Entry> entries;
    private final List<ItemStack> items;
    private List<SelectorWheelLayout.Face> geometry = List.of();
    private double scale = 1, originY, radius, scroll, maxScroll;
    private int lastX, lastY;
    private boolean pointerArmed, keyboardSelection, mouseReady;
    private Component status;
    private boolean failure;

    public ClassSelectorWheelScreen(ClassSelectorBindings controller, String style) {
        super(Component.translatable("ralle.settings.option.class-selector-enabled"));
        this.controller = controller;
        bars = "bars".equals(style);
        entries = controller.entries();
        items = entries.stream().map(e -> ClassPresentation.item(e.character())).toList();
        model.open(System.currentTimeMillis());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        for (var mapping : minecraft.options.keyMappings) mapping.setDown(false);
        rebuild();
        GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), minecraft.getWindow().getScreenWidth() / 2d,
                minecraft.getWindow().getScreenHeight() / 2d);
        lastX = width / 2; lastY = height / 2;
        pointerArmed = false;
        mouseReady = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), 0) != GLFW.GLFW_PRESS;
    }
    private void rebuild() {
        if (bars) {
            int labelWidth = entries.stream().mapToInt(e -> font.width(RalleTypography.body(Component.literal(e.name())))).max().orElse(0);
            int side = Math.max(5, Math.min(Math.max(160, (Math.min(100, labelWidth) + 12) * 5), (int) (width * .64)) / 5 * 5);
            geometry = SelectorWheelLayout.bars(entries.size(), side, width);
            scale = 1;
            double top = geometry.stream().mapToInt(SelectorWheelLayout.Face::top).min().orElse(0);
            double bottom = geometry.stream().mapToInt(SelectorWheelLayout.Face::bottom).max().orElse(0);
            maxScroll = Math.max(0, bottom - top - Math.max(1, height - 64));
            scroll = Math.clamp(scroll, 0, maxScroll);
            originY = maxScroll > 0 ? 12 - top - scroll : height / 2d;
            radius = bottom;
        } else {
            radius = Math.max(90, entries.size() * 42 / (2 * Math.PI) + 36);
            geometry = SelectorWheelLayout.circles(SelectorWheelModel.ring(entries.size(), radius - 64, radius, true));
            scale = Math.max(.01, Math.min(1, Math.min((width / 2d - 8) / (radius + 7), (height / 2d - 30) / (radius + 7))));
            originY = height / 2d;
        }
    }
    public void keyboardSelection(int index) {
        keyboardSelection = true;
        model.hover(index, System.currentTimeMillis());
        if (bars && maxScroll > 0 && index >= 0) {
            var face = geometry.get(index);
            double top = originY + face.top() - SelectorWheelModel.EXTENSION;
            double bottom = originY + face.bottom();
            if (top < 6) scroll -= 6 - top;
            else if (bottom > height - 42) scroll += bottom - (height - 42);
            rebuild();
        }
    }
    public void feedback(Component message, boolean failure) {
        status = message;
        this.failure = failure;
        if (controller.completed()) model.submit();
    }
    public void submitted(int index) {
        status = null;
        model.hover(index, System.currentTimeMillis());
        model.submit();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (mouseX != lastX || mouseY != lastY) {
            pointerArmed = true; keyboardSelection = false; lastX = mouseX; lastY = mouseY;
        }
        if (pointerArmed && !keyboardSelection && !controller.completed()) {
            int hovered = hit(mouseX, mouseY);
            model.hover(hovered >= 0 && !controller.unavailable(entries.get(hovered)) ? hovered : -1, System.currentTimeMillis());
        }
        if (bars) graphics.enableScissor(0, 6, width, Math.max(7, height - 42));
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, (float) originY);
        graphics.pose().scale((float) scale, (float) scale);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var face = geometry.get(index);
            double extension = model.offset(index, System.currentTimeMillis());
            int dx = (int) Math.round(face.motionX() * extension), dy = (int) Math.round(face.motionY() * extension);
            SelectorWheelRenderer.drawFace(graphics, face, dx, dy, model.hovered() == index);
            int x = (int) Math.round(face.centerX()) + dx, y = (int) Math.round(face.centerY()) + dy;
            graphics.renderItem(items.get(index), x - 8, y - 22);
            int color = controller.unavailable(entry) ? 0xFF7E899B : index == model.hovered() ? RalleTheme.accentArgb()
                    : org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText();
            int lineWidth = bars ? face.right() - face.left() - 6 : Math.max(24, (int) ((radius - 32) * 2 * Math.PI / Math.max(1, entries.size())) - 8);
            var lines = font.split(RalleTypography.body(Component.literal(entry.name())), Math.max(1, lineWidth));
            int lineY = y - 3;
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                var text = lines.get(line);
                graphics.drawString(font, text, x - font.width(text) / 2, lineY, color, false);
                lineY += font.lineHeight + 1;
            }
            var binding = controller.current(entry) ? RalleTypography.body(Component.translatable("ralle.classes.wheel.current"))
                    : keyLabel(entry.shortcut().binding());
            boolean conflict = controller.optionConflict(entry);
            if (conflict) binding = binding.copy().append(" !");
            graphics.drawString(font, binding, x - font.width(binding) / 2, y + 21, conflict ? 0xFFFF6B6B : color, false);
        }
        graphics.pose().popMatrix();
        if (bars) graphics.disableScissor();
        Component below = status != null ? status : entries.isEmpty() ? Component.translatable("ralle.classes.wheel.empty")
                : model.hovered() >= 0 ? Component.literal(entries.get(model.hovered()).name())
                : Component.translatable("ralle.classes.wheel.hint");
        var lines = font.split(RalleTypography.body(below), Math.max(1, width - 24));
        int y = entries.isEmpty() ? height / 2 : Math.min(height - lines.size() * (font.lineHeight + 2) - 4,
                (int) (originY + (radius + 12) * scale));
        for (var line : lines) {
            graphics.drawString(font, line, (width - font.width(line)) / 2, y, failure ? 0xFFFF6B6B : RalleTheme.accentArgb(), true);
            y += font.lineHeight + 2;
        }
    }
    private Component keyLabel(String binding) {
        if (ClassShortcut.UNBOUND.equals(binding)) return Component.empty();
        try { return RalleTypography.body(InputConstants.getKey(binding).getDisplayName()); }
        catch (IllegalArgumentException ignored) { return Component.empty(); }
    }
    private int hit(double x, double y) {
        if (bars && (y < 6 || y >= height - 42)) return -1;
        return SelectorWheelLayout.hit(geometry, (x - width / 2d) / scale, (y - originY) / scale);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (controller.completed()) return true;
        if (event.button() == 0) {
            boolean ready = mouseReady;
            mouseReady = false;
            int index = hit(event.x(), event.y());
            if (ready && index >= 0) { model.hover(index, System.currentTimeMillis()); controller.click(index, true); }
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { if (event.button() == 0) mouseReady = true; return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (bars && maxScroll > 0 && !controller.completed()) {
            scroll = Math.clamp(scroll - vertical * 24, 0, maxScroll);
            rebuild(); model.hover(-1, System.currentTimeMillis()); keyboardSelection = false;
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) { return controller.handleKeyboard(event, GLFW.GLFW_PRESS); }
    @Override public boolean keyReleased(KeyEvent event) { return controller.handleKeyboard(event, GLFW.GLFW_RELEASE); }
    @Override public void onClose() { controller.close(); }
    @Override public void removed() { controller.screenRemoved(this); super.removed(); }
    @Override public Component getNarrationMessage() {
        var narration = title.copy();
        for (var entry : entries) {
            narration.append(". ").append(entry.name()).append(" ").append(keyLabel(entry.shortcut().binding()));
            if (controller.unavailable(entry)) narration.append(". ").append(Component.translatable(controller.current(entry)
                    ? "ralle.classes.switch.current" : "ralle.classes.missing"));
        }
        return narration;
    }
}
