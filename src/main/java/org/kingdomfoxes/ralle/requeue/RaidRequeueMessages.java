package org.kingdomfoxes.ralle.requeue;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

/** Observes original server prompts even when another mod hides or rewrites their chat display. */
public final class RaidRequeueMessages {
    private RaidRequeueMessages() { }

    public static void register(Consumer<Component> observer) {
        // ALLOW_GAME calls every listener, including after another listener cancels display.
        // GAME runs only for allowed messages and receives the result of MODIFY_GAME.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) observer.accept(message);
            return true;
        });
    }
}
