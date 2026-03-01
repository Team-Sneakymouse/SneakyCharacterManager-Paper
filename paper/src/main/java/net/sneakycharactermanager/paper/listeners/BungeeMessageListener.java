package net.sneakycharactermanager.paper.listeners;

import java.security.PublicKey;
import java.security.Signature;
import java.util.*;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class BungeeMessageListener implements PluginMessageListener {

	private boolean verifySignature(byte[] messageBytes, String receivedSignature) {
		try {
			PublicKey publicKey = SneakyCharacterManager.getInstance().getPublicKey();
			if (publicKey == null) {
				SneakyCharacterManager.getInstance().getLogger().severe("No valid public key file was found.");
				return false;
			}

			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initVerify(publicKey);
			signature.update(messageBytes);
			return signature.verify(Base64.getDecoder().decode(receivedSignature));
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().severe("Error verifying signature: " + e.getMessage());
			return false;
		}
	}

	@Override
	public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
		if (!channel.equalsIgnoreCase("sneakymouse:" + SneakyCharacterManager.IDENTIFIER)) {
			return;
		}

		ByteArrayDataInput in = ByteStreams.newDataInput(message);

		String receivedSignature = in.readUTF();

		int remainingBytes = message.length - (receivedSignature.length() + 2);

		byte[] messageBytes = new byte[remainingBytes];
		in.readFully(messageBytes);

		if (!verifySignature(messageBytes, receivedSignature)) {
			SneakyCharacterManager.getInstance().getLogger().severe("Received a message with an invalid signature!");
			return;
		}

		ByteArrayDataInput messageIn = ByteStreams.newDataInput(messageBytes);
		String subChannel = messageIn.readUTF();

		switch (subChannel) {
			case "loadCharacter":
				String playerUUID = messageIn.readUTF();
				Character character = readCharacter(playerUUID, messageIn);
				boolean forced = messageIn.readBoolean();

				Player pl = Bukkit.getPlayer(UUID.fromString(playerUUID));

				if (!forced && !Character.canPlayerLoadCharacter(pl, character.getCharacterUUID())) {
					pl.sendMessage(ChatUtility.convertToComponent("&4You cannot access this character right now."));
					break;
				}

				character.load();
				break;
			case "selectCharacterByNameFailed":
				playerUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
				if (pl == null)
					return;

				pl.sendMessage(ChatUtility.convertToComponent("&aNo character found. Loading character menu..."));
				SneakyCharacterManager.getInstance().selectionMenu.openMenu(pl);
				break;
			case "loadTempCharacter":
				String requesterUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(requesterUUID));
				character = readCharacter(requesterUUID, messageIn);
				String characterSource = messageIn.readUTF();

				character.load();
				ConsoleCommandCharTemp.playerTempCharAdd(requesterUUID, characterSource, character.getCharacterUUID());
				break;
			case "loadTempCharacterFailed":
				playerUUID = messageIn.readUTF();
				String characterUUID = messageIn.readUTF();

				SneakyCharacterManager.getInstance().getLogger()
						.warning("An attempt was made to load a temp character but it did not exist: [" + playerUUID
								+ "," + characterUUID + "]");
				break;
			case "characterSelectionGUI":
				playerUUID = messageIn.readUTF();
				requesterUUID = messageIn.readUTF();
				List<Character> characters = readCharacterList(playerUUID, messageIn);

				SneakyCharacterManager.getInstance().selectionMenu.updateInventory(requesterUUID, characters);
				break;
			case "preloadSkins":
				playerUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
				if (pl == null)
					return;
				requesterUUID = messageIn.readUTF();
				characters = readCharacterList(requesterUUID, messageIn);

				// Online player loading their own skins → PRIO_ONLINE.
				// Offline player being bulk-preloaded → PRIO_PRELOAD.
				int skinPrio = requesterUUID.equals(playerUUID) ? SkinQueue.PRIO_ONLINE : SkinQueue.PRIO_PRELOAD;

				for (Character c : characters) {
					// Base skin preloading
					ProfileProperty p = SkinCache.get(playerUUID, c.getSkin());
					if (p == null) {
						SkinData.getOrCreate(c.getSkin(), c.getSkinUUID(), c.isSlim(), skinPrio, pl, c.getCharacterUUID(), c.getName());
					}

					// Uniform variant preloading
					for (Map.Entry<String, String[]> entry : c.getUniformVariants().entrySet()) {
						String vUUID = entry.getValue()[0];
						String vUrl = entry.getValue()[1];
						ProfileProperty vp = SkinCache.get(playerUUID, vUrl);
						if (vp == null) {
							SkinData.getOrCreate(vUrl, vUUID, c.isSlim(), skinPrio, pl, c.getCharacterUUID(), c.getName())
								.setUniformCacheInfo(c.getSkin(), entry.getKey());
						}
					}
				}
				break;
			case "updateCharacterList":
				playerUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
				assert pl != null;
				CommandChar.tabCompleteMap.put(pl.getUniqueId().toString(), readStringList(messageIn));
				break;
			case "defaultSkin":
				playerUUID = messageIn.readUTF();
				characterUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
				if (pl == null)
					return;
				PlayerProfile profile = pl.getPlayerProfile();
				PlayerTextures textures = profile.getTextures();
				if (textures.getSkin() == null)
					return;
				String skinURL = textures.getSkin().toString();
				boolean slim = textures.getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

				BungeeMessagingUtil.sendByteArray(pl, "defaultSkin", playerUUID, characterUUID, skinURL, slim);
				break;
			case "deleteConfirmed":
				playerUUID = messageIn.readUTF();
				pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
				pl.sendMessage(ChatUtility.convertToComponent("&aThe following character has been deleted: &b`"
						+ messageIn.readUTF() + "`&a (" + messageIn.readUTF() + ")"));
				break;
			case "getAllCharacters":
				playerUUID = messageIn.readUTF();
				List<String> characterData = readStringList(messageIn);
				handleCharacterOutput(playerUUID, characterData);
				break;
			default:
				SneakyCharacterManager.getInstance().getLogger().severe(
						"SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
				break;
		}
	}

	public static void handleCharacterOutput(String requesterUUID, List<String> characterData) {
		Player requester = Bukkit.getPlayer(UUID.fromString(requesterUUID));
		if (requester == null)
			return;

		requester.sendMessage(ChatUtility.convertToComponent("&eFound the following usernames: "));
		Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (_s) -> {

			for (String information : characterData) {
				String[] data = information.split("\\$");
				String playerUUID = data[0];
				String characterName = data[1];
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerUUID));

				if (!offlinePlayer.hasPlayedBefore())
					continue; // This shouldn't happen but just in case

				Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> requester.sendMessage(
						ChatUtility
								.convertToComponent(
										"&eUser: " + offlinePlayer.getName() + " &7| &aCharacter: " + characterName)
								.hoverEvent(HoverEvent
										.showText(ChatUtility.convertToComponent("&6Player UUID: " + playerUUID)))
								.clickEvent(ClickEvent.copyToClipboard(playerUUID))));

			}

		});

	}

	public static List<String> readStringList(ByteArrayDataInput in) {
		int size = in.readInt();

		List<String> strings = new ArrayList<>();
		while (strings.size() < size) {
			strings.add(in.readUTF());
		}

		return strings;
	}

	public static List<Character> readCharacterList(String uuid, ByteArrayDataInput in) {
		int size = in.readInt();

		List<Character> characters = new ArrayList<>();
		while (characters.size() < size) {
			Character character = readCharacter(uuid, in);
			characters.add(character);
		}

		return characters;
	}

	private static Character readCharacter(String playerUUID, ByteArrayDataInput in) {
		String uuid = in.readUTF();
		String name = in.readUTF();
		String skin = in.readUTF();
		String skinUUID = in.readUTF();
		boolean slim = in.readBoolean();
		String tags = in.readUTF();
		String gender = in.readUTF();
		
		Character character = new Character(playerUUID, uuid, name, skin, skinUUID, slim, tags, gender);
		
		// Read uniform variants
		int variantCount = in.readInt();
		for (int i = 0; i < variantCount; i++) {
			String hash = in.readUTF();
			String sUUID = in.readUTF();
			String tUrl = in.readUTF();
			character.addUniformVariant(hash, sUUID, tUrl);
		}
		
		return character;
	}

}