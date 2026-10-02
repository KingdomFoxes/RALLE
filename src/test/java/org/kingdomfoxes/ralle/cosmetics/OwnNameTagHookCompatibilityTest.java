package org.kingdomfoxes.ralle.cosmetics;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Check the exact Minecraft descriptors that reveal and fit the original nameplate. */
class OwnNameTagHookCompatibilityTest {
    @Test void playerVisibilityHookTargetsTheAvatarOverload() throws IOException {
        assertEquals(1, matchingMethods("net.minecraft.client.renderer.entity.player.AvatarRenderer",
                "shouldShowName", "(Lnet/minecraft/world/entity/Avatar;D)Z"));
    }

    @Test void inventoryExtractionScopeAndViewportHaveOneAnchorEach() throws IOException {
        assertEquals(1, matchingMethods("net.minecraft.client.gui.screens.inventory.InventoryScreen",
                "extractRenderState", "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"));
        int[] submissions = {0};
        new ClassReader("net.minecraft.client.gui.screens.inventory.InventoryScreen").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("renderEntityInInventoryFollowsMouse")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (owner.equals("net/minecraft/client/gui/GuiGraphics") && name.equals("submitEntityRenderState")
                                && descriptor.equals("(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIII)V"))
                            submissions[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, submissions[0]);
    }

    private static int matchingMethods(String type, String target, String targetDescriptor) throws IOException {
        int[] matches = {0};
        new ClassReader(type).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals(target) && descriptor.equals(targetDescriptor)) matches[0]++;
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return matches[0];
    }
}
