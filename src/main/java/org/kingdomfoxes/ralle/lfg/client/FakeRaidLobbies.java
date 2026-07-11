package org.kingdomfoxes.ralle.lfg.client;

import java.util.List;

import static org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby.Region.AS;
import static org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby.Region.EU;
import static org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby.Region.NA;
import static org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby.Status.IN_RAID;
import static org.kingdomfoxes.ralle.lfg.client.FakeRaidLobby.Status.OPEN;

/** Fixed sample state for evaluating the first owo-lib LFG layout. */
public final class FakeRaidLobbies {
    private FakeRaidLobbies() {}

    public static List<FakeRaidLobby> all() {
        return List.of(
                lobby("Dailies", EU, OPEN, false, "Chill full daily run",
                        member("aae87519-9835-4c46-8ac8-b9193c149f7e", "chalupe", "Fox", true),
                        member("b07644da-313e-4dc9-9f82-0ce867e972c8", "realkot1489", "Novu", false)),
                lobby("NOTG", NA, OPEN, false, "First-timers welcome",
                        member("238cc0de-4cbb-4b87-8e3f-3a956ba89bb4", "Greeeno", "Miku", true)),
                lobby("NOL", AS, OPEN, true, "Guild run - reopening soon",
                        member("9ae4d0ad-9ef0-452e-8b17-47613a8816a4", "SuperSmasherM", "Crrs", true),
                        member("f36497a2-9a15-4cfd-936f-3872b13c695b", "Velen", "TAq", false),
                        member("4af3d7e5-ba85-43b0-a5e6-bfdb84cf673a", "ReneCZ", "Imp", false)),
                lobby("TCC", EU, OPEN, false, "Fast run, bring buffs",
                        member("f934c5ba-38a5-4ff3-845c-c894bab20c26", "Vheal", "VETS", true),
                        member("f03f5944-23ce-4e26-98ef-16179b9d2c6b", "maxkarson", "Fox", false),
                        member("67c4480e-c05d-4d6d-bce2-a45192e4d64b", "EliteInferno531", "Novu", false)),
                lobby("TNA", NA, IN_RAID, false, "Experienced group",
                        member("e64bbf93-095f-4e2e-afba-a438c85c8d31", "Xcela", "Miku", true),
                        member("202763ae-0daa-40cc-b76b-38f0835434cb", "RedstoneFinder", "Crrs", false),
                        member("8eec8b95-3be3-4382-8bfe-e1066ebdcc2a", "FlQyD", "TAq", false),
                        member("06075991-a779-4a6e-bf4c-926b5805db12", "wikimedia_org", "Imp", false)),
                lobby("TWP", EU, OPEN, false, "Learning the mechanics",
                        member("90150913-157f-484f-a745-0793d385e8ca", "oVoidd", "VETS", true),
                        member("194845a1-cd34-43a7-9c35-a70c26bc0d90", "TheSmartFox", "Fox", false))
        );
    }

    private static FakeRaidLobby lobby(String raid, FakeRaidLobby.Region region, FakeRaidLobby.Status status,
                                       boolean locked, String note, FakeRaidLobby.Member... members) {
        return new FakeRaidLobby(raid, region, status, locked, note, List.of(members));
    }

    private static FakeRaidLobby.Member member(String uuid, String ign, String guild, boolean host) {
        return new FakeRaidLobby.Member(uuid, ign, guild, host);
    }
}
