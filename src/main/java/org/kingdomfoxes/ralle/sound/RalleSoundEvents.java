package org.kingdomfoxes.ralle.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.kingdomfoxes.ralle.RalleClient;

import java.util.EnumMap;
import java.util.Map;

public final class RalleSoundEvents {
    private static final Map<RalleSoundCue, SoundEvent> EVENTS = createEvents();
    private static boolean registered;

    private RalleSoundEvents() {}

    public static void register() {
        if (registered) return;
        EVENTS.values().forEach(event -> Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event));
        registered = true;
    }

    public static SoundEvent event(RalleSoundCue cue) {
        return EVENTS.get(cue);
    }

    private static Map<RalleSoundCue, SoundEvent> createEvents() {
        var events = new EnumMap<RalleSoundCue, SoundEvent>(RalleSoundCue.class);
        events.put(RalleSoundCue.XYLOPHONE_D5, event("ui.xylophone.d5"));
        events.put(RalleSoundCue.XYLOPHONE_E5, event("ui.xylophone.e5"));
        events.put(RalleSoundCue.XYLOPHONE_F_SHARP_5, event("ui.xylophone.f_sharp_5"));
        events.put(RalleSoundCue.XYLOPHONE_G5, event("ui.xylophone.g5"));
        events.put(RalleSoundCue.XYLOPHONE_A5, event("ui.xylophone.a5"));
        events.put(RalleSoundCue.XYLOPHONE_B5, event("ui.xylophone.b5"));
        events.put(RalleSoundCue.XYLOPHONE_C_SHARP_6, event("ui.xylophone.c_sharp_6"));
        events.put(RalleSoundCue.XYLOPHONE_D6, event("ui.xylophone.d6"));
        events.put(RalleSoundCue.XYLOPHONE_E6, event("ui.xylophone.e6"));
        events.put(RalleSoundCue.XYLOPHONE_F_SHARP_6, event("ui.xylophone.f_sharp_6"));
        events.put(RalleSoundCue.CHAT_COPY_SUCCESS, event("ui.chat_copy_success"));
        return Map.copyOf(events);
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, path));
    }
}
