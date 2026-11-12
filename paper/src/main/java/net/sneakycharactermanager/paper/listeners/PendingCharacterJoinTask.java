package net.sneakycharactermanager.paper.listeners;

import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;

final class PendingCharacterJoinTask implements Runnable {

    private final Player player;
    private final Runnable cancelTask;
    private final BooleanSupplier characterLoaded;
    private final Runnable requestCharacter;

    PendingCharacterJoinTask(
            Player player,
            Runnable cancelTask,
            BooleanSupplier characterLoaded,
            Runnable requestCharacter) {
        this.player = player;
        this.cancelTask = cancelTask;
        this.characterLoaded = characterLoaded;
        this.requestCharacter = requestCharacter;
    }

    @Override
    public void run() {
        if (!player.isOnline() || characterLoaded.getAsBoolean()) {
            cancelTask.run();
        } else {
            requestCharacter.run();
        }
    }
}
