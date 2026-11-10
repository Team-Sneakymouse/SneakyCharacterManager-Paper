package net.sneakycharactermanager.paper.handlers.character;

import org.jetbrains.annotations.ApiStatus;

/**
 * Why a character is being put on a body. Only decides what happens when another body already wears the character
 * (see {@link HolderRegistry}); otherwise every reason loads the same way.
 */
@ApiStatus.Internal
public enum LoadReason {
    /**
     * The owner joining as their last-played character, or a character they just created (the storage result is
     * "forced" for both). If another body wears it, the owner wins: that body is saved and released first.
     */
    OWN_JOIN,
    /** The owner picking one of their own characters (the {@code /char} menu or {@code /char <name>}). */
    OWN_SELECT,
    /**
     * A holder released for the owner getting its own last-played character back. Arrives as a join, but it isn't
     * one: if someone else wears that character, it is refused rather than taken over.
     */
    RETURN,
    /** Another player's character put on this body through the internal borrow seam. */
    BORROW,
    /** Any other load: a direct {@link Character#load()} call by another plugin, staff tooling, or a non-owner. */
    OTHER;

    /** The reason a storage result for the owner's own character stands for: forced means join or create. */
    public static LoadReason ownResult(boolean forced) {
        return forced ? OWN_JOIN : OWN_SELECT;
    }
}
