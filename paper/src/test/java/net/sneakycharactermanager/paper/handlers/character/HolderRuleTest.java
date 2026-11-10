package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.HolderRegistry.Decision;

/**
 * The holder rule as a table, for every reason a load can have (including create, which is a forced own result, and
 * a swap, which ticket 05 adds without a load). End-to-end cases are in {@code HolderRegistryTest}.
 */
class HolderRuleTest {
    final UUID owner = UUID.randomUUID();
    final UUID other = UUID.randomUUID();
    final UUID third = UUID.randomUUID();
    final CharacterKey key = new CharacterKey(owner, "c");

    @Test
    void aFreeCharacterOrOneTheBodyAlreadyWearsLoadsForEveryReason() {
        for (LoadReason reason : LoadReason.values()) {
            assertEquals(Decision.LOAD, HolderRegistry.decide(reason, owner, key, null), reason.name());
            assertEquals(Decision.LOAD, HolderRegistry.decide(reason, other, key, null), reason.name());
            assertEquals(Decision.LOAD, HolderRegistry.decide(reason, owner, key, owner), reason.name());
            assertEquals(Decision.LOAD, HolderRegistry.decide(reason, other, key, other), reason.name());
        }
    }

    @Test
    void theOwnerJoiningAsAHeldCharacterTakesItOver() {
        assertEquals(Decision.TAKE_OVER, HolderRegistry.decide(LoadReason.OWN_JOIN, owner, key, other));
    }

    @Test
    void theOwnerPickingAHeldCharacterGoesToTheSelectionHandler() {
        assertEquals(Decision.SELECTION_HANDLER, HolderRegistry.decide(LoadReason.OWN_SELECT, owner, key, other));
    }

    @Test
    void everythingElseOnAHeldCharacterIsRefused() {
        assertEquals(Decision.REFUSE, HolderRegistry.decide(LoadReason.BORROW, owner, key, other));
        assertEquals(Decision.REFUSE, HolderRegistry.decide(LoadReason.OTHER, owner, key, other));
        for (LoadReason reason : LoadReason.values()) {
            // A non-owner never gets the owner's exceptions, whatever the reason claims (a forced staff load included).
            assertEquals(Decision.REFUSE, HolderRegistry.decide(reason, third, key, other), reason.name());
            assertEquals(Decision.REFUSE, HolderRegistry.decide(reason, other, key, owner), reason.name());
        }
    }

    @Test
    void storageResultsMapToOwnReasons() {
        assertEquals(LoadReason.OWN_JOIN, LoadReason.ownResult(true));   // join, create
        assertEquals(LoadReason.OWN_SELECT, LoadReason.ownResult(false)); // menu, by name
    }
}
