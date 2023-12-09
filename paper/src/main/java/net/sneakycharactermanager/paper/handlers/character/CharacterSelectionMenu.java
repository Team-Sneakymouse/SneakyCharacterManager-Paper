package net.sneakycharactermanager.paper.handlers.character;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class CharacterSelectionMenu implements Listener {

    Map<String, CharacterMenuHolder> activeMenus;

    protected NamespacedKey characterKey;

    public class CharacterMenuHolder implements InventoryHolder {

        private final String playerUUID;
        private Inventory inventory;

        boolean updated = false;

        public CharacterMenuHolder(String playerUUID){
            this.playerUUID = playerUUID;
            Player player = Bukkit.getPlayer(UUID.fromString(playerUUID));
            if(player == null) return;
            inventory = Bukkit.createInventory(this, 54,
                    ChatUtility.convertToComponent("&e" + player.getName() + "'s Characters")
            );
            requestCharacterList();
        }

        private void requestCharacterList(){
            BungeeMessagingUtil.sendByteArray("characterSelectionGUI", playerUUID);
        }

        public void receivedCharacterList(List<BungeeMessageListener.CharacterSnapshot> characterSnapshotList){
            if(updated) return;
            for(BungeeMessageListener.CharacterSnapshot snapshot : characterSnapshotList){
                addItem(this.getInventory(), snapshot);
            }
            updated = true;
        }

        private void addItem(Inventory inventory, BungeeMessageListener.CharacterSnapshot snapshot){
            ItemStack characterHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) characterHead.getItemMeta();

            skullMeta.displayName(ChatUtility.convertToComponent("&e" + snapshot.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(ChatUtility.convertToComponent("&5" + snapshot.getUUID()));
            skullMeta.lore(lore);

            skullMeta.getPersistentDataContainer().set(characterKey, PersistentDataType.STRING, snapshot.getUUID());

            characterHead.setItemMeta(skullMeta);

            Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) ->{
                SkinData data = new SkinData(snapshot.getSkin(), snapshot.isSlim());
                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () ->{
                    ProfileProperty property = data.getTextureProperty();
                    PlayerProfile profile = Bukkit.getPlayer(UUID.fromString(playerUUID)).getPlayerProfile();
                    if(property == null) return;

                    profile.setProperty(property);

                    //Updating Skin URL with Mojang URL
                    BungeeMessagingUtil.sendByteArray("updateCharacter",
                            playerUUID, 1, profile.getTextures().getSkin().toString());

                    skullMeta.setPlayerProfile(profile);
                    characterHead.setItemMeta(skullMeta);
                    inventory.addItem(characterHead);
                });
            });
        }

        private void clickedItem(ItemStack clickedItem){
            if(!clickedItem.getType().equals(Material.PLAYER_HEAD)) return;

            Player player = Bukkit.getPlayer(UUID.fromString(playerUUID));
            if(player == null) return;

            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
            String characterUUID = meta.getPersistentDataContainer().get(characterKey, PersistentDataType.STRING);
            if(characterUUID == null) return;

            player.sendMessage(ChatUtility.convertToComponent("&aLoading your character... Please Wait..."));
            player.closeInventory();
            BungeeMessagingUtil.sendByteArray("selectCharacter", playerUUID, characterUUID);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public CharacterSelectionMenu(){
        activeMenus = new HashMap<>();
        characterKey = NamespacedKey.fromString("character", SneakyCharacterManager.getInstance());
    }

    private boolean menuExists(String uuid){
        return activeMenus.containsKey(uuid);
    }

    public void openMenu(Player player){
        if(!menuExists(player.getUniqueId().toString())){
            CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId().toString());
            player.openInventory(holder.getInventory());
            activeMenus.put(player.getUniqueId().toString(), holder);
        }else{ //This should ever happen but just incase!
            CharacterMenuHolder holder = activeMenus.get(player.getUniqueId().toString());
            player.openInventory(holder.getInventory());
        }
    }

    public void updateInventory(String playerUUID, List<BungeeMessageListener.CharacterSnapshot> characterSnapshotList){
        if(!menuExists(playerUUID)){
            SneakyCharacterManager.getInstance().getLogger().warning("Attempted to update invalid inventory!");
            return;
        }
        CharacterMenuHolder holder = activeMenus.get(playerUUID);
        if(holder == null) return; //Also should be possible
        holder.receivedCharacterList(characterSnapshotList);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        if(event.getClickedInventory() == null) return;

        if(event.getView().getTopInventory().getHolder() instanceof CharacterMenuHolder){
            event.setCancelled(true);
        }

        Inventory inventory = event.getClickedInventory();

        if(!(inventory.getHolder() instanceof CharacterMenuHolder characterMenuHolder)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if(clickedItem == null) return;
        characterMenuHolder.clickedItem(clickedItem);
    }

    @EventHandler
    public void onInventoryInteract(InventoryInteractEvent event){
        Inventory inventory = event.getView().getTopInventory();
        if(!(inventory.getHolder() instanceof CharacterMenuHolder characterMenuHolder)) return;
        event.setCancelled(true); //Shouldnt be able to interact with anything if they are in Character Selection
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event){
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();
        if(!(inventory.getHolder() instanceof CharacterMenuHolder)) return;
        activeMenus.remove(player.getUniqueId().toString()); //Remove holder to save memory
    }

}
