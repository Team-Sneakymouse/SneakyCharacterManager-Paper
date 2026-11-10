package net.sneakycharactermanager.paper.api.character;

/**
 * How a {@link Puppets#swap} call ended. Only {@link #SWAPPED} exchanged the characters. Every other result except
 * {@link #INTERRUPTED} changed nothing; {@link #REFUSED} may have saved both characters where they stood.
 */
public enum SwapResult {
    /** Each body now wears the other's character, where it stood. */
    SWAPPED,
    /** SCM runs in legacy proxy mode; swapping needs SneakyDB. */
    UNSUPPORTED,
    /** A body isn't online. */
    BODY_OFFLINE,
    /** Both arguments are the same body. */
    SAME_BODY,
    /** A body wears no character, for example while a released puppet waits for its own character. */
    NOT_WEARING,
    /** A body is dead. */
    DEAD,
    /** The bodies are in different worlds. The caller moves them into one world first. */
    DIFFERENT_WORLDS,
    /** A body is in a bed. The caller gets it out first. */
    SLEEPING,
    /**
     * A body or one of the two characters is in the middle of a load or another swap, for example when called from a
     * {@code LoadCharacterEvent} listener. Trying again later may work.
     */
    BUSY,
    /**
     * A listener refused a teleport, or a body quit or lost its character before anything was exchanged. Nothing was
     * exchanged; any body that had moved is back where it stood.
     */
    REFUSED,
    /**
     * Something took a body off its new character, or a body quit, while the swap ran (a listener of one of the
     * swap's own events). Both characters were saved where they stood before the exchange began; whatever each body
     * wears now is what it wears.
     */
    INTERRUPTED;

    public boolean isSuccess() {
        return this == SWAPPED;
    }
}
