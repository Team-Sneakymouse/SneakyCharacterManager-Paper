package net.sneakycharactermanager.bungee.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PluginMessageListener implements Listener {

    @EventHandler
    public void on(PluginMessageEvent event)
    {
        if (!event.getTag().equalsIgnoreCase( "SneakyCharacterManager" ) )
        {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput( event.getData() );
        String subChannel = in.readUTF();

        if (subChannel.equals("Placeholder")) {
            // Read and process your data from 'in'
        }
    }
}