package org.kingdomfoxes.ralle.requeue;

import java.util.ArrayList;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidRequeueMessagesTest {
    @Test
    void observesAnyInitiatorBeforeCancellationOrRewritingAndIgnoresActionBars() {
        var hiddenAnnouncement = Component.literal("OtherPlayer would like to start Nest of the Grootslangs!");
        var ready = Component.literal("Click here to Ready Up!");
        var nickedPrompt = Component.literal("SecretNickname")
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Real username: OtherPlayer"))))
                .append(" would like to start The Nameless Anomaly! Click here to Ready Up!");
        var tracker = new RaidReadyTracker();
        var remembered = new ArrayList<WynnRaid>();

        // Register the hiding mod first to verify cancellation does not stop observation.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> message != hiddenAnnouncement);
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                message == nickedPrompt ? Component.literal("Party is ready") : message);
        RaidRequeueMessages.register(message -> tracker.observe(message.getString(), 0).ifPresent(remembered::add));

        assertFalse(ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(hiddenAnnouncement, false));
        assertTrue(ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(ready, false));
        assertEquals(java.util.List.of(WynnRaid.NOTG), remembered);

        assertTrue(ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(nickedPrompt, false));
        assertEquals("Party is ready", ClientReceiveMessageEvents.MODIFY_GAME.invoker()
                .modifyReceivedGameMessage(nickedPrompt, false).getString());
        assertEquals(java.util.List.of(WynnRaid.NOTG, WynnRaid.TNA), remembered);

        assertTrue(ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(nickedPrompt, true));
        assertEquals(2, remembered.size());
    }
}
