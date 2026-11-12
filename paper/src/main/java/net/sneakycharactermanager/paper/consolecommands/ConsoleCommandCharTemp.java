package net.sneakycharactermanager.paper.consolecommands;

/**
 * Template characters and the {@code chartemp} command were removed. This stub remains only so
 * plugins compiled against the old API (SneakyRacials) still link.
 *
 * @deprecated always returns {@code false}; drop the call.
 */
@Deprecated(forRemoval = true)
public final class ConsoleCommandCharTemp {

    private ConsoleCommandCharTemp() {}

    /** @deprecated template characters no longer exist; always {@code false}. */
    @Deprecated(forRemoval = true)
    public static boolean isPlayerTempChar(String playerUUID) {
        return false;
    }
}
