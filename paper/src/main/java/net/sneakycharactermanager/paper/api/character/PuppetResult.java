package net.sneakycharactermanager.paper.api.character;

/** How a {@link Puppets#puppet} call ended. Only {@link #PUPPETED} put the character on the body. */
public enum PuppetResult {
    /** The body now wears the character. */
    PUPPETED,
    /** SCM runs in legacy proxy mode; puppeting needs SneakyDB. Nothing was loaded. */
    UNSUPPORTED,
    /** The body isn't online. */
    BODY_OFFLINE,
    /** The character belongs to the body itself; that is a normal {@code /char} switch, not a puppet. */
    OWN_CHARACTER,
    /** Another body (the owner included) wears the character, either when asked or by the time the load arrived. */
    HELD,
    /** The owner has no such character, or it is disabled. */
    NOT_FOUND,
    /**
     * The load was cut short and nothing was put on the body: the owner or the body logged in or out while it was on
     * its way, a newer load for the body replaced it, or the owner's character changed while it loaded. Trying again
     * may work.
     */
    INTERRUPTED,
    /** The load was refused when it arrived, for example a {@code LoadCharacterEvent} listener cancelled it. */
    REFUSED,
    /** Character storage failed, or SCM is shutting down. */
    FAILED;

    public boolean isSuccess() {
        return this == PUPPETED;
    }
}
