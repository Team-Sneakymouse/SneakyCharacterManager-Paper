package net.sneakycharactermanager.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class BungeeMessageListener implements PluginMessageListener
{
    
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equalsIgnoreCase("sneakymouse:sneakycharactermanager"))
        {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        
        switch (subChannel) {
            case "loadCharacter" :
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                String characterName = in.readUTF();
                String skin = in.readUTF();
                
                //TODO: Load skin, apply nickname, and load inventory
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }

}