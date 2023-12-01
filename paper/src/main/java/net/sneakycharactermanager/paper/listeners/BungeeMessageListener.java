package net.sneakycharactermanager.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

public class BungeeMessageListener implements PluginMessageListener
{
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equalsIgnoreCase("SneakyCharacterManager"))
        {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        if (subChannel.equalsIgnoreCase("Placeholder"))
        {

        }
    }

}