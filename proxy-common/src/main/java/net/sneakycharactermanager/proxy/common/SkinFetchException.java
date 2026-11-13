package net.sneakycharactermanager.proxy.common;

import java.io.IOException;

/**
 * Failure while validating or fetching a skin image. The {@link Reason} is safe to log and to map to a
 * player-facing message; {@link #getMessage()} may contain hosts or addresses and must stay in the server log.
 */
public final class SkinFetchException extends IOException {

    public enum Reason {
        /** Not an absolute http(s) URL, has user info, or uses a port that is not allowed. */
        INVALID_URL(true),
        /** Host is not on the configured allow-list. */
        HOST_NOT_ALLOWED(true),
        /** Host resolves to a loopback, private, link-local or otherwise non-public address. */
        ADDRESS_NOT_PUBLIC(true),
        TOO_MANY_REDIRECTS(true),
        /** Body is larger than the configured byte cap. */
        TOO_LARGE(true),
        /** Not a PNG, or not 64x64 / 64x32. */
        NOT_A_SKIN_PNG(true),
        /** Remote answered with a non-200 status. */
        HTTP_STATUS(false),
        TIMEOUT(false),
        NETWORK(false);

        private final boolean rejection;

        Reason(boolean rejection) {
            this.rejection = rejection;
        }

        /** True when retrying elsewhere (for example MineSkin) cannot help: the URL or image itself is unacceptable. */
        public boolean isRejection() {
            return rejection;
        }
    }

    private final Reason reason;

    public SkinFetchException(Reason reason, String detail) {
        super(reason.name() + ": " + detail);
        this.reason = reason;
    }

    public SkinFetchException(Reason reason, String detail, Throwable cause) {
        super(reason.name() + ": " + detail, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public boolean isRejection() {
        return reason.isRejection();
    }
}
