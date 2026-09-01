package net.sneakycharactermanager.paper.listeners;

import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;

final class PendingCharacterJoinTask implements Runnable {

    private final Player player;
    private final Runnable cancelTask;
    private final BooleanSupplier characterLoaded;
    private final BooleanSupplier temporaryCharacter;
    private final Runnable reapplyTemporaryCharacter;
    private final Runnable requestCharacter;

    PendingCharacterJoinTask(
            Player player,
            Runnable cancelTask,
            BooleanSupplier characterLoaded,
            BooleanSupplier temporaryCharacter,
            Runnable reapplyTemporaryCharacter,
            Runnable requestCharacter) {
        this.player = player;
        this.cancelTask = cancelTask;
        this.characterLoaded = characterLoaded;
        this.temporaryCharacter = temporaryCharacter;
        this.reapplyTemporaryCharacter = reapplyTemporaryCharacter;
        this.requestCharacter = requestCharacter;
    }

    @Override
    public void run() {
        if (!player.isOnline() || characterLoaded.getAsBoolean()) {
            cancelTask.run();
        } else if (temporaryCharacter.getAsBoolean()) {
            reapplyTemporaryCharacter.run();
        } else {
            requestCharacter.run();
        }
    }
}
