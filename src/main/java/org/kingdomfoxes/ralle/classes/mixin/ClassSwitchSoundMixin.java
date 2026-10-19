package org.kingdomfoxes.ralle.classes.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.kingdomfoxes.ralle.classes.ClassSwitchAudio;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep sounds ticking normally; filter only the gain sent to Minecraft's OpenAL channels. */
@Mixin(Channel.class)
abstract class ClassSwitchSoundMixin {
    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private float ralle$classSwitchVolume(float volume) { return ClassSwitchAudio.outputVolume(volume); }
}
