package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildTerritoryColorsTest {
    @Test
    void territoryColorsOverrideGeneratedServiceColorsWhileUnknownGuildsKeepTheirFallback() {
        assertEquals(0xFFFF8200, GuildTerritoryColors.forGuild(" Fox ", "#83D742"));
        assertEquals(0xFFCE4F4F, GuildTerritoryColors.forGuild("NOVU", "#83D742"));
        assertEquals(0xFF123456, GuildTerritoryColors.forGuild("Other", "#123456"));
        assertEquals(0xFF657087, GuildTerritoryColors.forGuild(null, "invalid"));
    }
}
