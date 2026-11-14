package net.sneakycharactermanager.paper.api.character;

/** How a {@link Puppets#release} call ended. */
public enum ReleaseResult {
    /** The borrowed character was saved and taken off; the body's own last-played character is on its way. */
    RELEASED,
    /** The body wasn't wearing another player's character. Nothing changed. */
    NOT_PUPPETED,
    /** SCM runs in legacy proxy mode; puppeting needs SneakyDB. Nothing changed. */
    UNSUPPORTED
}
