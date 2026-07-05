package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.*;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;


/**
 * Manager class for any function involving Nicknames and Nametags
 * */
public class NametagManager {

    private final Set<String> hidingOwnName;

    private final Map<String, Nickname> nicknames;

    public NametagManager() {
        nicknames = new HashMap<>();
        hidingOwnName = new HashSet<>();
    }

    /**
     * Set the nickname of the supplied player to the given name
     * @param player Player to create/set the nickname for.
     * @param nickname Nickname to set onto the player
     * */
    public void nicknamePlayer(Player player, String nickname) {
        if (player.isDead() ||
            player.getGameMode() == GameMode.SPECTATOR ||
            (
                SneakyCharacterManager.getInstance().papiActive &&
                PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null &&
                !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty() &&
                !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").equals("%cmi_user_vanished_symbol%")
            )
        ) return;

        if (!nicknames.containsKey(player.getUniqueId().toString())) {
            nicknames.put(player.getUniqueId().toString(), new Nickname(player, nickname));
        }else{
            nicknames.get(player.getUniqueId().toString()).setNickname(nickname);
        }

        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            Nickname name = nicknames.get(player.getUniqueId().toString());
            if (name == null) return;

            refreshNicknames(player, name, null);
        }, 5);

    }

    public List<Player> refreshNicknames(Player player, Nickname name, List<Player> trackingPlayersPrev) {
        List<Player> currentTrackers = new ArrayList<>();

        for (Player tracking : player.getTrackedBy()) {
            if (player.getLocation().distanceSquared(tracking.getLocation()) > 10000) continue;
            currentTrackers.add(tracking);
            if (trackingPlayersPrev != null && trackingPlayersPrev.contains(tracking)) continue;
            refreshNickname(name, tracking);
        }
        if (SneakyCharacterManager.getInstance().getConfig().getBoolean("see-own-nameplate", false)) {
            currentTrackers.add(player);
            if (trackingPlayersPrev == null || !trackingPlayersPrev.contains(player)) {
                refreshNickname(name, player);
            }
        }

        return currentTrackers;
    }

    public void refreshNickname(Nickname name, Player requester) {
        refreshNickname(name, requester, NamesPreferenceHandler.get(requester));
    }

    public void refreshNickname(Nickname name, Player requester, NamesPreference preference) {
        switch (preference) {
            case OFF -> name.hideName(requester);
            case ON -> {
                if (hidingOwnName.contains(name.getOwnerUuid())) {
                    name.showHiddenName(requester);
                } else {
                    name.showRealName(requester, true);
                }
            }
            case CHARACTER -> {
                if (hidingOwnName.contains(name.getOwnerUuid())) {
                    name.hideName(requester);
                } else {
                    name.showRealName(requester, false);
                }
            }
        }
    }

    public void applyNamesPreference(Player requester) {
        applyNamesPreference(requester, NamesPreferenceHandler.get(requester));
    }

    public void applyNamesPreference(Player requester, NamesPreference preference) {
        for (Nickname name : nicknames.values()) {
            refreshNickname(name, requester, preference);
        }

        if (SneakyCharacterManager.getInstance().getConfig().getBoolean("see-own-nameplate", false)) {
            Nickname own = nicknames.get(requester.getUniqueId().toString());
            if (own != null) {
                refreshNickname(own, requester, preference);
            }
        }
    }

    public void setHidingOwnName(Player player, boolean hide) {
        String uuid = player.getUniqueId().toString();
        if (hide) {
            hidingOwnName.add(uuid);
        } else {
            hidingOwnName.remove(uuid);
        }

        Nickname nickname = nicknames.get(uuid);
        if (nickname != null) {
            refreshNicknames(player, nickname, null);
        }
    }

    public boolean isHidingOwnName(Player player) {
        return hidingOwnName.contains(player.getUniqueId().toString());
    }

    public void clearHidingOwnName(Player player) {
        hidingOwnName.remove(player.getUniqueId().toString());
    }

    /**
     * Remove the nickname of a player
     * @param player Player to remove the nickname from
     * */
    public void unnicknamePlayer(Player player) {
        Nickname nickname = nicknames.remove(player.getUniqueId().toString());
        if (nickname != null) {
            nickname.unNick();
        }
    }

    /**
     * Load all active nicknames for a player.
     * Required because the nickname entities are fake, they do not exist on player connect
     * @param player Player to load names for
     * */
    public void loadNames(Player player) {
        applyNamesPreference(player);
    }

    /**
     * Get the players current Nickname!
     * @return The Players Nickname, or null if they don't have one.
     * */
    public Nickname getNickname(Player player) {
        return nicknames.get(player.getUniqueId().toString());
    }

    public void setTalking(Player player, boolean talking) {
        Nickname nickname = nicknames.get(player.getUniqueId().toString());
        if (nickname != null) {
            nickname.setTalking(talking);
        }
    }

    /**
     * Remove the nickname entities of all players.
     * */
    public void unnickAll() {
        nicknames.values().forEach(entry -> {
            entry.unNick();
        });
    }

}
