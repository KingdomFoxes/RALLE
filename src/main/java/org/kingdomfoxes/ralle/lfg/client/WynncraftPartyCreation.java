package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.ui.owo.WynncraftPartyQueuePrompt;
import org.kingdomfoxes.ralle.war.hqdistance.WynntilsCompatibility;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** Shared pre-create flow for the browser, keybind chord, and selector wheel. */
public final class WynncraftPartyCreation {
    private WynncraftPartyCreation() {}

    public static CompletableFuture<LfgProtocol.Mutation> create(RaidLfgService service,
            LfgProtocol.RaidType raid, LfgProtocol.Region region, String note) {
        return create(service, raid, region, note, () -> {});
    }

    public static CompletableFuture<LfgProtocol.Mutation> create(RaidLfgService service,
            LfgProtocol.RaidType raid, LfgProtocol.Region region, String note, Runnable beforeSubmit) {
        var viewer = service.store().state().viewer();
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE || viewer == null) {
            return CompletableFuture.failedFuture(new LfgCreationFeedback.CreationException("unavailable"));
        }
        var party = current(viewer.ign());
        if (party.isEmpty()) {
            beforeSubmit.run();
            return service.create(raid, region, note);
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        return WynncraftPartyQueuePrompt.show(party.get()).thenCompose(choice -> {
            if (choice == WynncraftPartyQueuePrompt.Choice.CANCEL) {
                return CompletableFuture.failedFuture(new CancellationException("Lobby creation cancelled."));
            }
            if (minecraft.getConnection() != connection || service.lifecycle() != RaidLfgService.LifecycleState.ONLINE
                    || !viewer.equals(service.store().state().viewer())) {
                return CompletableFuture.failedFuture(new LfgCreationFeedback.CreationException("connection-changed"));
            }
            if (choice == WynncraftPartyQueuePrompt.Choice.SOLO) {
                beforeSubmit.run();
                return service.create(raid, region, note);
            }
            if (!current(viewer.ign()).equals(party)) {
                return CompletableFuture.failedFuture(new LfgCreationFeedback.CreationException("party-changed"));
            }
            beforeSubmit.run();
            return service.createWithParty(raid, region, note, party.get());
        });
    }

    private static Optional<List<String>> current(String localIgn) {
        if (!WynntilsCompatibility.detect().supported()) return Optional.empty();
        try {
            return WynntilsPartySnapshotSource.current().otherMembersForHost(localIgn);
        } catch (LinkageError | RuntimeException unavailable) {
            return Optional.empty();
        }
    }
}
