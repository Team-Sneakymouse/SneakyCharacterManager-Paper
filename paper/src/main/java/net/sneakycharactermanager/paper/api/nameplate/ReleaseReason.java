package net.sneakycharactermanager.paper.api.nameplate;

/**
 * Why a {@link NameplateHandle} stopped being active. Every reason is terminal: the handle is inert afterwards and a
 * new {@link NameplateAttachments#attach attach} call is required.
 */
public enum ReleaseReason {
    /** The consumer called {@link NameplateHandle#close()}. */
    CLOSED,
    /** The plate owner left the server. */
    OWNER_QUIT,
    /** The consumer plugin was disabled. */
    CONSUMER_DISABLED,
    /** SneakyCharacterManager was disabled. */
    SCM_DISABLED,
    /** The anchor entity was removed, unloaded, became invalid, or refused the plate as a passenger. */
    ANCHOR_INVALID,
    /** The plate owner moved to another world. */
    OWNER_CHANGED_WORLD
}
