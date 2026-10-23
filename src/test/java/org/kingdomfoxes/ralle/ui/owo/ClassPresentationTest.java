package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.classes.ClassCharacter;
import org.kingdomfoxes.ralle.classes.ClassSprite;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassPresentationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void classIconsUseTheReceivedResourcePackModelAndTintWithoutCopyingAssets() {
        for (float model : List.of(575F, 580F, 582F, 584F)) {
            var character = new ClassCharacter("id", "Mage", "", new ClassSprite(model, 0xFFFFFF));
            var icon = ClassPresentation.item(character);
            assertTrue(icon.is(Items.POTION));
            assertEquals(List.of(model), icon.get(DataComponents.CUSTOM_MODEL_DATA).floats());
            assertEquals(0xFFFFFF, icon.get(DataComponents.POTION_CONTENTS).customColor().orElseThrow());
            assertNull(icon.get(DataComponents.CUSTOM_NAME));
            assertTrue(icon.get(DataComponents.LORE).lines().isEmpty());
        }
        assertTrue(ClassPresentation.item(null).is(Items.PAPER));
        assertTrue(ClassPresentation.item(new ClassCharacter("id", "Mage", "")).is(Items.PAPER));
    }
}
