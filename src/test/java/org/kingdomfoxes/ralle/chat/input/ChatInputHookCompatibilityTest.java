package org.kingdomfoxes.ralle.chat.input;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** These optional injections must not silently stop matching a new Minecraft build. */
class ChatInputHookCompatibilityTest {
    @Test
    void inputClickObservationHasExactlyOnePreMutationAnchor() throws IOException {
        assertEquals(1, calls("net.minecraft.client.multiplayer.MultiPlayerGameMode", "handleInventoryMouseClick",
                "net/minecraft/world/inventory/AbstractContainerMenu", "clicked",
                "(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"));
    }

    @Test
    void serverPromptObservationHasExactlyOnePreDisplayAnchor() throws IOException {
        assertEquals(1, calls("net.minecraft.client.multiplayer.ClientPacketListener", "handleSystemChat",
                "net/minecraft/client/multiplayer/chat/ChatListener", "handleSystemMessage",
                "(Lnet/minecraft/network/chat/Component;Z)V"));
    }

    @Test
    void manualAndServerMenuClosesUseSeparatePaths() throws IOException {
        assertEquals(1, calls("net.minecraft.client.player.LocalPlayer", "closeContainer",
                "net/minecraft/client/player/LocalPlayer", "clientSideCloseContainer", "()V"));
        assertEquals(1, calls("net.minecraft.client.multiplayer.ClientPacketListener", "handleContainerClose",
                "net/minecraft/client/player/LocalPlayer", "clientSideCloseContainer", "()V"));
    }

    private static int calls(String type, String method, String owner, String target, String descriptor) throws IOException {
        int[] count = {0};
        new ClassReader(type).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals(method)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String actualOwner, String name, String desc, boolean isInterface) {
                        if (actualOwner.equals(owner) && name.equals(target) && desc.equals(descriptor)) count[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count[0];
    }
}
