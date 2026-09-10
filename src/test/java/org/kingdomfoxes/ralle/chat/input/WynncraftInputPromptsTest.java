package org.kingdomfoxes.ralle.chat.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WynncraftInputPromptsTest {
    private static final String PREFIX = "\uDAFF\uDFFC\uE00A\uDAFF\uDFFF\uE002\uDAFF\uDFFE ";
    private static final String WRAP = "\n\uDAFF\uDFFC\uE001\uDB00\uDC06 ";

    @Test
    void recognizesMarketSearchQuantityAndWrappedPrice() {
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + WRAP + "Type the item name or type 'cancel' to cancel:" + WRAP));
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + "Type the amount you wish to buy or type 'cancel' to cancel:"));
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + "Type the amount you wish to sell or type 'cancel' to cancel:"));
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + "Type the price in emeralds or formatted (e.g '10eb', '10stx"
                + WRAP + "5eb') or type 'cancel' to cancel:"));
    }

    @Test
    void recognizesObservedRecruitAndNicknamePrompts() {
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + "Please write in chat the username of the player you want"
                + WRAP + "to recruit, or cancel."));
        assertTrue(WynncraftInputPrompts.isPrompt(PREFIX + "§7Type in chat the new nickname you would like to use,"
                + WRAP + "'clear' to remove a nickname or 'cancel' (16 characters" + WRAP + "max)"));
    }

    @Test
    void directInstructionsSupportOtherNamedChatEntryWithoutAcceptingChatQuotes() {
        assertTrue(WynncraftInputPrompts.isPrompt("Type the new pet name in chat."));
        assertTrue(WynncraftInputPrompts.isPrompt("Please enter the guild name in chat, or cancel."));
        assertFalse(WynncraftInputPrompts.isPrompt(PREFIX + "FriendFox: Type the new pet name in chat."));
        assertFalse(WynncraftInputPrompts.isPrompt("Type /renamepet name in chat"));
        assertFalse(WynncraftInputPrompts.isPrompt("Notice somebody breaking the rules? Type /report in chat"));
        assertFalse(WynncraftInputPrompts.isPrompt("Your pet's name has been changed."));
        assertFalse(WynncraftInputPrompts.isPrompt("x".repeat(4097)));
    }

    @Test
    void screenshotVerifiedMenuActionsExcludeNavigationAndOrdinaryItems() {
        assertTrue(WynncraftInputPrompts.isInputAction("Kingdom Foxes: Diplomacy", "§aAdd Ally",
                "Click to add a new guild alliance by name or tag Used Slots: 6/7"));
        assertTrue(WynncraftInputPrompts.isInputAction("Recruit a Friend", "Recruit a Friend",
                "Enter the name of a friend you want to recruit and get rewards! Click to recruit a friend!"));
        assertFalse(WynncraftInputPrompts.isInputAction("Kingdom Foxes: Diplomacy", "Back", ""));
        assertFalse(WynncraftInputPrompts.isInputAction("Chest", "Add Ally", ""));
        assertFalse(WynncraftInputPrompts.isInputAction("Recruit a Friend", "Recruit a Friend", "You cannot recruit another friend"));
    }

    @Test
    void recognizesInputTooltipsAndPetRenameActions() {
        assertTrue(WynncraftInputPrompts.isInputAction("Pets", "Rename Pet", ""));
        assertTrue(WynncraftInputPrompts.isInputAction("Pet Settings", "Rename", ""));
        assertTrue(WynncraftInputPrompts.isInputAction("Name Editor", "Edit Name", "Click to type a new name in chat"));
        assertFalse(WynncraftInputPrompts.isInputAction("Pet Settings", "Summon Pet", ""));
        assertFalse(WynncraftInputPrompts.isInputAction("Pet Settings", "Rename Pet", "You cannot rename pets"));
        assertFalse(WynncraftInputPrompts.isInputAction("Chest", "Rename", ""));
    }
}
