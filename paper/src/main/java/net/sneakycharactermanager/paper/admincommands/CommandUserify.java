package net.sneakycharactermanager.paper.admincommands;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.listeners.ProxyMessageListener;
import net.sneakycharactermanager.paper.util.ProxyListReplies;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import org.bukkit.Bukkit;
import com.sneakyutils.util.TextUtility;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandUserify extends CommandBaseAdmin {
    public CommandUserify() {
        super("userify");
        this.setAliases(java.util.List.of("whois"));
        this.usageMessage = this.getName() + " <Character Name>";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if(args.length == 0) {
            sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }
        if(!(sender instanceof Player player)) return false;

        StringBuilder userBuilder = new StringBuilder();
        for(int i = 0; i < args.length; i++){
            userBuilder.append(args[i]).append(" ");
        }
        String characterName = userBuilder.substring(0, userBuilder.length()-1);

        String requester = player.getUniqueId().toString();
        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        if (plugin.getCharacterBackend() != null) {
            ProxyMessagingUtil.sendByteArray(player, "getAllCharacters", requester, characterName);
            return false;
        }
        // Legacy: the proxy replies in size-limited chunks. A proxy that predates chunking ignores the request id and
        // sends its old single reply, which ProxyMessageListener still prints; this future then just times out.
        ProxyListReplies.request("getAllCharacters", requester, characterName).whenComplete((results, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Character search got no complete proxy reply: " + error);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> ProxyMessageListener.handleCharacterOutput(requester, results));
        });
        return false;
    }
}
