package net.sneakycharactermanager.paper.handlers.character;

import java.io.File;
import java.io.IOException;
import java.util.*;

import net.sneakycharactermanager.paper.util.InventoryUtility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import net.sneakycharactermanager.paper.util.SkinUtil;

public class CharacterSelectionMenu implements Listener {

    Map<String, CharacterMenuHolder> activeMenus;

    protected NamespacedKey characterKey;

    public static final String CHARACTER_SLOTS_PERMISSION_NODE = SneakyCharacterManager.IDENTIFIER + ".characterslots.";
    private static final Component CREATE_CHARACTER = ChatUtility.convertToComponent("&2Create Character");
    private static final Component CHARACTER_SLOTS_FULL = ChatUtility.convertToComponent("&4No Character Slots Remaining");

    public class CharacterMenuHolder implements InventoryHolder {

        protected static String LEFT;
        protected static String SWAP_OFFHAND;

        private final String playerUUID;

        protected OfflinePlayer offlinePlayer = null;
        protected Player player = null;
        protected final Player opener;
        private Inventory inventory;
        private List<SkinData> queuedDatas = new ArrayList<>();

        boolean updated = false;

        public CharacterMenuHolder(OfflinePlayer player, Player opener) {
            setTooltipStrings();

            if(player.isOnline()){
                this.player = (Player) player;
            }else{
                this.offlinePlayer = player;
            }

            int size = 9;
            if(this.player != null){
                if (CommandChar.tabCompleteMap.containsKey(this.player.getUniqueId().toString())) {
                    size = Math.min((int) Math.floor((CommandChar.tabCompleteMap.get(this.player.getUniqueId().toString()).size() + 1) / 9) * 9 + 9, 54);
                }
            }else {
                if (CommandChar.tabCompleteMap.containsKey(this.offlinePlayer.getUniqueId().toString())) {
                    size = Math.min((int) Math.floor((CommandChar.tabCompleteMap.get(this.offlinePlayer.getUniqueId().toString()).size() + 1) / 9) * 9 + 9, 54);
                }
            }

            this.opener = opener;
            if (this.player == null){
                this.playerUUID = this.offlinePlayer.getUniqueId().toString();
                inventory = Bukkit.createInventory(this, size,
                        ChatUtility.convertToComponent("&e" + this.offlinePlayer.getName() + "'s Characters")
                );
            }else{
                this.playerUUID = this.player.getUniqueId().toString();
                inventory = Bukkit.createInventory(this, size,
                        ChatUtility.convertToComponent("&e" + this.player.getName() + "'s Characters")
                );
            }



            requestCharacterList();
        }

        protected void setTooltipStrings() {
            LEFT = "&eL-Click: &bSelect character.";
            SWAP_OFFHAND = "&eF: &bBegin character deletion. You will be asked to confirm.";
        }

        private void requestCharacterList() {
            BungeeMessagingUtil.sendByteArray("characterSelectionGUI", playerUUID, opener.getUniqueId().toString());
        }

        private void receivedCharacterList(List<Character> characters) {
            if (updated) return;

            for (int i = 0; i < characters.size(); i++) {
                addItem(this.getInventory(), characters.get(i), i);
            }

            updated = true;
        }

        private void addItem(Inventory inventory, Character character, int index) {
            if (index > inventory.getSize()) return;

            ItemStack characterHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) characterHead.getItemMeta();

            skullMeta.displayName(ChatUtility.convertToComponent("&e" + character.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(ChatUtility.convertToComponent(LEFT));
            lore.add(ChatUtility.convertToComponent(SWAP_OFFHAND));
            skullMeta.lore(lore);

            skullMeta.getPersistentDataContainer().set(characterKey, PersistentDataType.STRING, character.getCharacterUUID());
            skullMeta.setOwningPlayer((Bukkit.getOfflinePlayer("MHF_Alex")));
            characterHead.setItemMeta(skullMeta);

            ProfileProperty profileProperty = SkinCache.get(playerUUID, character.getSkin());

            if (profileProperty == null) {
                inventory.setItem(index, characterHead);

                SkinData data = SkinData.getOrCreate(character.getSkin(), character.isSlim(), 1);
                this.queuedDatas.add(data);

                Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
                    if(player == null){
                        SkinUtil.waitForSkinProcessing(this.offlinePlayer, data);
                        Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                            ProfileProperty p = SkinCache.get(playerUUID, character.getSkin());
                            if (p != null) {
                                updateHead(skullMeta, this.offlinePlayer, p, characterHead, inventory, index);
                                this.queuedDatas.remove(data);
                                //if (this.queuedDatas.isEmpty()) SneakyCharacterManager.getInstance().skinPreloader.preLoadedPlayers.add(this.offlinePlayer);
                            }
                        });
                    }else{
                        SkinUtil.waitForSkinProcessing(this.player, data);
                        Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                            ProfileProperty p = SkinCache.get(playerUUID, character.getSkin());
                            if (p != null) {
                                updateHead(skullMeta, this.player, p, characterHead, inventory, index);
                                this.queuedDatas.remove(data);
                                if (this.queuedDatas.isEmpty()) SneakyCharacterManager.getInstance().skinPreloader.preLoadedPlayers.add(this.player);
                            }
                        });
                    }
                });
            } else {
                if(this.player == null){
                    updateHead(skullMeta, this.offlinePlayer, profileProperty, characterHead, inventory, index);
                }else{
                    updateHead(skullMeta, this.player, profileProperty, characterHead, inventory, index);
                }
            }
        }

        private static void updateHead(SkullMeta skullMeta, OfflinePlayer player, ProfileProperty profileProperty, ItemStack characterHead, Inventory inventory, int index) {
            skullMeta.setPlayerProfile(SkinUtil.handleCachedSkin(player, profileProperty));
            characterHead.setItemMeta(skullMeta);
            inventory.setItem(index, characterHead);
        }

        protected void clickedItem(ItemStack clickedItem) {
            if (!clickedItem.getType().equals(Material.PLAYER_HEAD)) return;

            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();

            if (meta.displayName().equals(CREATE_CHARACTER)) {
                BungeeMessagingUtil.sendByteArray("createNewCharacter", playerUUID);
                this.player.sendMessage(ChatUtility.convertToComponent("&aCreating a new character... Please Wait..."));
                this.player.sendMessage(ChatUtility.convertToComponent("&aOnce the character is created, use the /nick and /skin command to customize it!"));
                return;
            } else if (meta.displayName().equals(CHARACTER_SLOTS_FULL)) {
                return;
            }

            String characterUUID = meta.getPersistentDataContainer().get(characterKey, PersistentDataType.STRING);

            Character currentChar = Character.get(this.player);

            if (currentChar == null) return;

            if (characterUUID == null) return;
            if (characterUUID.equals(currentChar.getCharacterUUID())) {
                this.player.sendMessage(ChatUtility.convertToComponent("&aYou are already playing that character."));
                return;
            }

            this.player.sendMessage(ChatUtility.convertToComponent("&aLoading your character... Please Wait..."));
            this.player.closeInventory();
            BungeeMessagingUtil.sendByteArray("selectCharacter", playerUUID, characterUUID);
        }

        
        protected void swapItem(ItemStack clickedItem) {
            if (!clickedItem.getType().equals(Material.PLAYER_HEAD)) return;

            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();

            if (meta.displayName().equals(CREATE_CHARACTER) || meta.displayName().equals(CHARACTER_SLOTS_FULL)) return;

            String characterUUID = meta.getPersistentDataContainer().get(characterKey, PersistentDataType.STRING);

            Character currentChar = Character.get(this.player);

            if (currentChar == null) return;

            if (characterUUID == null) return;
            if (characterUUID.equals(currentChar.getCharacterUUID())) {
                this.player.sendMessage(ChatUtility.convertToComponent("&aYou cannot delete the character that you are currently playing."));
                return;
            }

            this.player.sendMessage(ChatUtility.convertToComponent("&aDeleting character '" + ((TextComponent) meta.displayName()).content() + "'. Type '/char confirm' within 10 seconds to confirm deletion."));
            Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
                this.player.closeInventory();
            }, 1);
            CommandChar.deleteConfirmationMap.put(this.player, System.currentTimeMillis() + ";" + characterUUID);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void cleanup() {
            for (SkinData skinData : queuedDatas) {
                skinData.cancel();
            }
        }
    }

    public class AdminInventoryHolder implements InventoryHolder {

        private Player opener;
        private OfflinePlayer target;
        private String characterUUID;
        private ItemStack[] contents;

        private Inventory inventory;

        public AdminInventoryHolder(Player opener, OfflinePlayer target, String characterUUID, ItemStack[] contents){
            this.opener = opener;
            this.target = target;
            this.characterUUID = characterUUID;
            this.contents = contents;

            createInventory();
        }

        private void createInventory(){
            this.inventory = Bukkit.createInventory(this, 45, ChatUtility.convertToComponent("&eEditing Inventory"));
            this.inventory.setContents(contents);
        }

        public void onClose(){
            Character currentChar = null;
            Player player = Bukkit.getPlayer(this.target.getUniqueId());
            if(player != null){
                currentChar = Character.get(player);
            }

            if(currentChar != null && currentChar.getCharacterUUID().equals(this.characterUUID)){
                this.opener.sendMessage(ChatUtility.convertToComponent("&4Inventory not saved! &ePlayer swapped character while you were editing!"));
                return;
            }

            ItemStack[] dummyItems = new ItemStack[41];
            for(int i = 0; i < dummyItems.length; i++){
                dummyItems[i] = this.inventory.getContents()[i];
            }

            String encoded = InventoryUtility.inventoryToBase64(41, dummyItems);

            File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.target.getUniqueId().toString());
            File characterFile = new File(playerDir, this.characterUUID + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
            config.set("inventory", encoded);

            try {
                config.save(characterFile);
                this.opener.sendMessage(ChatUtility.convertToComponent("&eSaved character inventory!"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
    
    public class CharadminMenuHolder extends CharacterMenuHolder {

        public CharadminMenuHolder(OfflinePlayer player, Player opener) {
            super(player, opener);
        }
        
        @Override
        protected void setTooltipStrings() {
            LEFT = "&eL-Click: &bNothing yet.";
            SWAP_OFFHAND = "&eF: &bOpen inventory.";
        }

        @Override
        protected void clickedItem(ItemStack clickedItem) {
            return;
        }

        @Override
        protected void swapItem(ItemStack clickedItem) {
            if (!clickedItem.getType().equals(Material.PLAYER_HEAD)) return;

            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();

            String characterUUID = meta.getPersistentDataContainer().get(characterKey, PersistentDataType.STRING);

            if (characterUUID == null) return;
            Character currentChar = null;
            if(this.player != null){
                currentChar = Character.get(player);
            }

            if (currentChar != null && characterUUID.equals(currentChar.getCharacterUUID())) {
                this.opener.sendMessage(ChatUtility.convertToComponent("&aThe player is currently on this character. Use direct inventory editing instead."));
                return;
            }

            this.opener.sendMessage(ChatUtility.convertToComponent("&aEditing inventory for character '" + ((TextComponent) meta.displayName()).content() + "'."));
            
            Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
                // TODO: Open character inventory GUI
                String playerUUID;
                if(player == null)
                    playerUUID = offlinePlayer.getUniqueId().toString();
                else
                    playerUUID = player.getUniqueId().toString();

                File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID);
                File characterFile = new File(playerDir, characterUUID + ".yml");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);

                ItemStack[] inventoryContents = InventoryUtility.getSavedInventory(config.getString("inventory"));

                AdminInventoryHolder holder = new AdminInventoryHolder(this.opener, (this.player == null ? this.offlinePlayer : this.player), characterUUID, inventoryContents);
                this.opener.openInventory(holder.getInventory());

            }, 2);
        }
    
    }

    public CharacterSelectionMenu() {
        activeMenus = new HashMap<>();
        characterKey = NamespacedKey.fromString("character", SneakyCharacterManager.getInstance());
    }

    private boolean menuExists(String uuid) {
        return activeMenus.containsKey(uuid);
    }

    public void openMenu(Player player) {
        if (!menuExists(player.getUniqueId().toString())) {
            CharacterMenuHolder holder = new CharacterMenuHolder(player, player);
            player.openInventory(holder.getInventory());
            activeMenus.put(player.getUniqueId().toString(), holder);
        }else{ //This should ever happen but just incase!
            CharacterMenuHolder holder = activeMenus.get(player.getUniqueId().toString());
            player.openInventory(holder.getInventory());
        }
    }

    public void openAdminMenu(OfflinePlayer player, Player opener) {
        if (!menuExists(opener.getUniqueId().toString())) {
            CharadminMenuHolder holder = new CharadminMenuHolder(player, opener);
            opener.openInventory(holder.getInventory());
            activeMenus.put(opener.getUniqueId().toString(), holder);
        }else{ //This should ever happen but just incase!
            CharadminMenuHolder holder = (CharadminMenuHolder) activeMenus.get(opener.getUniqueId().toString());
            opener.openInventory(holder.getInventory());
        }
    }

    public void updateInventory(String playerUUID, List<Character> characters) {
        if (!menuExists(playerUUID)) {
            SneakyCharacterManager.getInstance().getLogger().warning("Attempted to update invalid inventory!");
            return;
        }
        CharacterMenuHolder holder = activeMenus.get(playerUUID);
        if (holder == null) return; //Also should be possible
        holder.receivedCharacterList(characters);

        if (holder.getClass().equals(CharacterMenuHolder.class)) {
            int maxCharacterSlots = 0;

            Player player = Bukkit.getPlayer(UUID.fromString(playerUUID));

            if (player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".*") || player.hasPermission(CHARACTER_SLOTS_PERMISSION_NODE + ".*")) maxCharacterSlots = 54;
            else {
                for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
                    if (permission.getPermission().startsWith(CHARACTER_SLOTS_PERMISSION_NODE)) {
                        String valueString = permission.getPermission().replace(CHARACTER_SLOTS_PERMISSION_NODE, "");

                        try {
                            int value = Integer.parseInt(valueString);
                        if (value > maxCharacterSlots) maxCharacterSlots = value;
                        } catch (NumberFormatException e) {}
                    }
                }
                maxCharacterSlots = Math.min(maxCharacterSlots, 54);
            }

            if (characters.size() < holder.getInventory().getSize()) {
                ItemStack createCharacterButton = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) createCharacterButton.getItemMeta();
                meta.setPlayerProfile(player.getPlayerProfile());

                if (maxCharacterSlots > characters.size()) {
                    meta.displayName(CREATE_CHARACTER);
                    meta.setOwningPlayer((Bukkit.getOfflinePlayer("MHF_Steve")));
                } else {
                    meta.displayName(CHARACTER_SLOTS_FULL);
                    meta.setOwningPlayer((Bukkit.getOfflinePlayer("MHF_Zombie")));
                }

                createCharacterButton.setItemMeta(meta);
                holder.getInventory().setItem((int) Math.floor(characters.size() / 9) * 9 + 8, createCharacterButton);
            }
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        if (event.getView().getTopInventory().getHolder() instanceof CharacterMenuHolder) {
            event.setCancelled(true);
        }

        Inventory inventory = event.getClickedInventory();

        if (!(inventory.getHolder() instanceof CharacterMenuHolder characterMenuHolder)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        if (event.getClick().equals(ClickType.LEFT)) characterMenuHolder.clickedItem(clickedItem);
        else if (event.getClick().equals(ClickType.SWAP_OFFHAND)) characterMenuHolder.swapItem(clickedItem);
    }

    @EventHandler
    public void onInventoryInteract(InventoryInteractEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof CharacterMenuHolder characterMenuHolder)) return;
        event.setCancelled(true); //Shouldnt be able to interact with anything if they are in Character Selection
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();
        if (!(inventory.getHolder() instanceof CharacterMenuHolder)){
            if(inventory.getHolder() instanceof AdminInventoryHolder adminInventoryHolder){
                adminInventoryHolder.onClose();
            }
            return;
        }
        activeMenus.get(player.getUniqueId().toString()).cleanup();
        activeMenus.remove(player.getUniqueId().toString()); //Remove holder to save memory
    }

}
