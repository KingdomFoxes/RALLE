package org.kingdomfoxes.ralle.ui.owo;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.classes.ClassCharacter;
import org.kingdomfoxes.ralle.classes.ClassSprite;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClassPresentationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void classIconsResolveSavedArtworkAgainstTheActivePackWithoutChangingTint() {
        var definition = JsonParser.parseString("""
                {"model":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data","entries":[
                    {"threshold":585,"model":{"model":"item/wynn/gui/character_select/icon/archer"}},
                    {"threshold":590,"model":{"model":"minecraft:item/wynn/gui/character_select/icon/mage"}}
                ]}}
                """);
        var selectors = ClassPresentation.iconSelectors(definition, path -> true);
        for (float model : List.of(575F, 585F, 580F, 590F)) {
            // Artwork is independent of the character's displayed class and nickname.
            var character = new ClassCharacter("id", "Mage", "", new ClassSprite(model, 0xFFFFFF));
            var icon = ClassPresentation.item(character, selectors);
            assertTrue(icon.is(Items.POTION));
            assertEquals(List.of(model == 575F || model == 585F ? 585F : 590F),
                    icon.get(DataComponents.CUSTOM_MODEL_DATA).floats());
            assertEquals(0xFFFFFF, icon.get(DataComponents.POTION_CONTENTS).customColor().orElseThrow());
            assertNull(icon.get(DataComponents.CUSTOM_NAME));
            assertTrue(icon.get(DataComponents.LORE).lines().isEmpty());
        }
        assertTrue(ClassPresentation.item(null, selectors).is(Items.PAPER));
        assertTrue(ClassPresentation.item(new ClassCharacter("id", "Mage", ""), selectors).is(Items.PAPER));
        assertTrue(ClassPresentation.item(new ClassCharacter("id", "Mage", "", new ClassSprite(580F, 0xFFFFFF)),
                Map.of()).is(Items.PAPER));
    }
}
