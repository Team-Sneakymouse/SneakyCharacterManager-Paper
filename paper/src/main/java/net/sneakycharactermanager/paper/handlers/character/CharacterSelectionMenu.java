package net.sneakycharactermanager.paper.handlers.character;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

        protected static String LEFT = "&eL-Click: &bSelect character.";
        protected static String SWAP_OFFHAND = "&eF: &bBegin character deletion. You will be asked to confirm.";

        protected final String playerUUID;
        protected final Player player;
        protected final Player opener;
        protected Inventory inventory;
        protected List<SkinData> queuedDatas = new ArrayList<>();

        boolean updated = false;

        public CharacterMenuHolder(Player player, Player opener) {
            this.player = player;
            this.opener = opener;
            this.playerUUID = player.getUniqueId().toString();
            if (this.player == null) return;

            int size = 9;

            if (CommandChar.tabCompleteMap.containsKey(this.player)) {
                size = Math.min((int) Math.floor((CommandChar.tabCompleteMap.get(this.player).size() + 1) / 9) * 9 + 9, 54);
            }

            inventory = Bukkit.createInventory(this, size,
                    ChatUtility.convertToComponent("&e" + this.player.getName() + "'s Characters")
            );
            requestCharacterList();
        }

        protected void requestCharacterList() {
            BungeeMessagingUtil.sendByteArray("characterSelectionGUI", playerUUID, opener.getUniqueId().toString());
        }

        public void receivedCharacterList(List<Character> characters) {
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
                    SkinUtil.waitForSkinProcessing(this.player, data);
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        ProfileProperty p = SkinCache.get(playerUUID, character.getSkin());
                        if (p != null) {
                            updateHead(skullMeta, this.player, p, characterHead, inventory, index);
                            this.queuedDatas.remove(data);
                            if (this.queuedDatas.isEmpty()) SneakyCharacterManager.getInstance().skinPreloader.preLoadedPlayers.add(this.player);
                        }
                    });
                });
            } else {
                updateHead(skullMeta, this.player, profileProperty, characterHead, inventory, index);
            }
        }

        private static void updateHead(SkullMeta skullMeta, Player player, ProfileProperty profileProperty, ItemStack characterHead, Inventory inventory, int index) {
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
    
    public class CharadminMenuHolder extends CharacterMenuHolder {

        static {
            LEFT = "&eL-Click: &bNothing yet.";
            SWAP_OFFHAND = "&eF: &bOpen inventory.";
        }

        public CharadminMenuHolder(Player player, Player opener) {
            super(player, opener);
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

            Character currentChar = Character.get(player);

            if (currentChar != null && characterUUID.equals(currentChar.getCharacterUUID())) {
                this.opener.sendMessage(ChatUtility.convertToComponent("&aThe player is currently on this character. Use direct inventory editing instead."));
                return;
            }

            this.opener.sendMessage(ChatUtility.convertToComponent("&aEditing inventory for character '" + ((TextComponent) meta.displayName()).content() + "'."));

            // TODO: Open character inventory GUI

            Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
                player.closeInventory();
            }, 1);
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

    public void openAdminMenu(Player player, Player opener) {
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
        if (!(inventory.getHolder() instanceof CharacterMenuHolder)) return;
        activeMenus.get(player.getUniqueId().toString()).cleanup();
        activeMenus.remove(player.getUniqueId().toString()); //Remove holder to save memory
    }

}
