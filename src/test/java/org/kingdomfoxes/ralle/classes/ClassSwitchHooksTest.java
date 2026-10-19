package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Validate required mixin boundaries against the real mapped Minecraft bytecode without launch/auth. */
class ClassSwitchHooksTest {
    @Test void presentationHookIsAfterTheFullRender() throws Exception {
        var minecraft = read("net/minecraft/client/Minecraft");
        var runTick = method(minecraft, "runTick", "(Z)V");
        long present = 0;
        for (var instruction : runTick.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("com/mojang/blaze3d/pipeline/RenderTarget")
                && call.name.equals("blitToScreen") && call.desc.equals("()V")) present++;
        assertEquals(1, present);
    }

    @Test void packetAndInputBoundariesMatchTheSupportedMinecraftVersion() throws Exception {
        var packets = read("net/minecraft/client/multiplayer/ClientPacketListener");
        for (String[] signature : new String[][] {
                {"handleOpenScreen", "ClientboundOpenScreenPacket"},
                {"setActionBarText", "ClientboundSetActionBarTextPacket"},
                {"handleContainerClose", "ClientboundContainerClosePacket"},
                {"handleSystemChat", "ClientboundSystemChatPacket"}})
            method(packets, signature[0], "(Lnet/minecraft/network/protocol/game/" + signature[1] + ";)V");
        var keyboard = read("net/minecraft/client/KeyboardHandler");
        method(keyboard, "keyPress", "(JILnet/minecraft/client/input/KeyEvent;)V");
        method(keyboard, "charTyped", "(JLnet/minecraft/client/input/CharacterEvent;)V");
        var mouse = read("net/minecraft/client/MouseHandler");
        method(mouse, "onButton", "(JLnet/minecraft/client/input/MouseButtonInfo;I)V");
        method(mouse, "onScroll", "(JDD)V");
        method(mouse, "handleAccumulatedMovement", "()V");
        method(mouse, "turnPlayer", "(D)V");
        for (String name : new String[] {"isLeftPressed", "isMiddlePressed", "isRightPressed"}) field(mouse, name, "Z");
        for (String name : new String[] {"accumulatedDX", "accumulatedDY", "mousePressedTime"}) field(mouse, name, "D");
        field(mouse, "activeButton", "Lnet/minecraft/client/input/MouseButtonInfo;");
        method(read("net/minecraft/client/multiplayer/MultiPlayerGameMode"), "handleInventoryMouseClick",
                "(IIILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V");
        method(read("com/mojang/blaze3d/audio/Channel"), "setVolume", "(F)V");
        method(read("net/minecraft/client/sounds/SoundManager"), "refreshCategoryVolume", "(Lnet/minecraft/sounds/SoundSource;)V");
    }

    @Test void observedPacketHooksRunBeforeOtherModsCanCancelDisplayOrMenuCreation() throws Exception {
        var signals = read("org/kingdomfoxes/ralle/classes/mixin/ClassMenuSignalsMixin");
        var mixin = signals.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        assertTrue((int) value(mixin, "priority") > 1000);
        int hooks = 0;
        for (var method : signals.methods) for (var annotation : method.visibleAnnotations == null
                ? List.<AnnotationNode>of() : method.visibleAnnotations) {
            if (!annotation.desc.endsWith("/Inject;")) continue;
            hooks++;
            @SuppressWarnings("unchecked") var locations = (List<AnnotationNode>) value(annotation, "at");
            assertEquals("HEAD", value(locations.getFirst(), "value"), method.name);
            assertEquals(1, value(annotation, "require"));
        }
        assertEquals(4, hooks);
    }

    @Test void stageUseRunsThroughVanillasActualInputHandlerAndDoesNotConstructAnInteractionRoute() throws Exception {
        var nativeInput = method(read("net/minecraft/client/Minecraft"), "handleKeybinds", "()V");
        assertTrue(calls(nativeInput, "net/minecraft/client/Minecraft", "startUseItem") > 0);
        var runtime = read("org/kingdomfoxes/ralle/classes/ClassSwitcher");
        var stage = method(runtime, "rightClickSelectionStage", "()V");
        assertEquals(0, calls(stage, "net/minecraft/client/multiplayer/MultiPlayerGameMode", "interact"));
        assertEquals(0, calls(stage, "net/minecraft/client/multiplayer/MultiPlayerGameMode", "useItemOn"));
        var beforeInput = method(runtime, "beforeInput", "()V");
        assertEquals(1, calls(beforeInput, "org/kingdomfoxes/ralle/classes/ClassStageInput", "queueUse"));
        method(read("net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl"), "send", "(Lnet/minecraft/network/protocol/Packet;)V");
    }
    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        throw new AssertionError("Missing " + key);
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = ClassSwitchHooksTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
    private static org.objectweb.asm.tree.MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + node.name + "." + name + descriptor));
    }
    private static void field(ClassNode node, String name, String descriptor) {
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)), name);
    }
}
