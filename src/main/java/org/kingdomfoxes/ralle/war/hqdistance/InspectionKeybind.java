package org.kingdomfoxes.ralle.war.hqdistance;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.lwjgl.glfw.GLFW;

/** Hold binding polled directly because the guild map consumes normal key events. */
final class InspectionKeybind {
    private final KeybindSetting setting;
    private final KeyMapping mapping;
    private String applied;

    InspectionKeybind(KeybindSetting setting) {
        this.setting = setting;
        mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ralle.hq-distance",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ralle", "war"))));
        synchronize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> synchronize());
    }

    private void synchronize() {
        String vanilla = mapping.saveString();
        if (InputConstants.UNKNOWN.getName().equals(vanilla)) vanilla = KeybindSetting.UNBOUND;
        String value = synchronizedValue(setting.value(), applied, vanilla);
        if (value.equals(applied)) return;
        setting.set(value);
        mapping.setKey(key(value));
        applied = value;
        KeyMapping.resetMapping();
    }

    static String synchronizedValue(String settingValue, String appliedValue, String vanillaValue) {
        return appliedValue == null || !settingValue.equals(appliedValue) ? settingValue : vanillaValue;
    }

    private static InputConstants.Key key(String value) {
        if (KeybindSetting.UNBOUND.equals(value)) return InputConstants.UNKNOWN;
        try { return InputConstants.getKey(value); }
        catch (IllegalArgumentException ignored) { return InputConstants.UNKNOWN; }
    }

    boolean held() {
        synchronize();
        var key = key(setting.value());
        if (key == InputConstants.UNKNOWN) return false;
        var window = Minecraft.getInstance().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        // Preserve either-Ctrl behavior for the default binding.
        if (key.getValue() == GLFW.GLFW_KEY_LEFT_CONTROL) {
            return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
