package net.sneakycharactermanager.paper.api.nameplate;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of {@link NameplateAttachments#attach}. Either {@link Attached} with the consumer's handle, or
 * {@link Refused} with a reason; a refusal changes nothing.
 */
public sealed interface AttachResult permits AttachResult.Attached, AttachResult.Refused {

    /** The attachment is active (or was already active for the same consumer and has been moved). */
    record Attached(NameplateHandle handle) implements AttachResult {
        public Attached {
            Objects.requireNonNull(handle, "handle");
        }
    }

    /** Nothing changed. */
    record Refused(Reason reason) implements AttachResult {
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        /** Another plugin already holds an active attachment for this owner (first attachment wins). */
        CONFLICT,
        /** The owner is not online. */
        OWNER_OFFLINE,
        /** The anchor is null, not valid, an SCM nameplate, or refused the plate as a passenger. */
        ANCHOR_INVALID,
        /** The anchor is not in the owner's world. */
        DIFFERENT_WORLD,
        /** The anchor is the owner; detach with {@link NameplateHandle#close()} instead. */
        ANCHOR_IS_OWNER,
        /** The consumer plugin is null or not enabled. */
        CONSUMER_DISABLED,
        /** SneakyCharacterManager is disabling or disabled. */
        SCM_DISABLED
    }

    /** The handle when attached, empty when refused. */
    default Optional<NameplateHandle> attachedHandle() {
        return this instanceof Attached attached ? Optional.of(attached.handle()) : Optional.empty();
    }

    /** The refusal reason when refused, empty when attached. */
    default Optional<Reason> refusedReason() {
        return this instanceof Refused refused ? Optional.of(refused.reason()) : Optional.empty();
    }
}
