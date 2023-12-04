package net.sneakycharactermanager.bungee.listeners;

import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ConnectionEventListeners implements Listener {
    
    @EventHandler
    public void onPlayerConnect(PostLoginEvent event) {
        // TODO: This function needs to be rethought and moved over to the backend

        // Check if the server that the player connected to is running the Paper plugin

        // Get player UUID

        // Get player's bungee character data

        // If bungee character data doesn't exist yet: Make bungee character data, then send packet to backend to make a first character

        // If bungee character data does exist:
        // Grab UUID of last played character
        // Grab matching skin and nickname data
        // Pack these in a standard "load character packet", and send them to the backend

        // In either case: If the backend's "deleteCharacterDataOnServerStart" config is false and the player did not yet have a folder in "characterdata", then the character that they end up on should copy the player's minecraft inventory
        // This is handled at the backend.
    }

}
