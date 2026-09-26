package org.kingdomfoxes.ralle.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.GuiMessage;
import org.kingdomfoxes.ralle.chat.ChatTimestampStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Verified against Wynntils 4.2.7: enable's consumer replays each original into recipient tabs. */
@Pseudo
@Mixin(targets = "com.wynntils.services.chat.ChatTabService", remap = false)
abstract class WynntilsChatTimestampMixin {
    @WrapMethod(method = "lambda$enable$3", require = 0)
    private static void ralle$preserveReplayTime(GuiMessage message, Operation<Void> original) {
        ChatTimestampStore.SESSION.replay(message, () -> original.call(message));
    }
}
