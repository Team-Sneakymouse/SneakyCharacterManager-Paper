package net.sneakycharactermanager.paper.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.CharacterUpdates;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyContext;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinFetching;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinState;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.handlers.character.CharacterSkinChangeEvent;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.util.SkinUtil;

public class CommandSkin extends CommandBase {

	public CommandSkin() {
		super("skin");
		this.description = "Change your skin!";
		this.setUsage("/skin [URL (Must be direct image)] (slim/classic)");
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

		if (!(sender instanceof Player player)) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Must be a player to run this command"));
			return false;
		}

		if (args.length < 1) {
			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Invalid Usage: " + this.getUsage()));
			return true;
		}

		if (args[0].equalsIgnoreCase("fetch")) {
			Player target = player;
			if (args.length > 1 && sender.hasPermission(SneakyCharacterManager.IDENTIFIER + ".skinfetch.others")) {
				target = Bukkit.getPlayer(args[1]);
				if (target == null) {
					player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cCould not find requested player!"));
					return false;
				}
			}

			PlayerProfile profile = target.getPlayerProfile();
			PlayerTextures textures = profile.getTextures();
			URL skinURL = textures.getSkin();
			if (skinURL == null) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cPlayer doesn't not have a valid Skin URL!"));
				return false;
			}

			player.sendMessage(
					TextUtility.INSTANCE.convertToComponent("&eHere is " + target.getName() + "'s skin url! Click to Copy!"));
			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&6" + skinURL)
					.clickEvent(ClickEvent.copyToClipboard(skinURL.toString()))
					.hoverEvent(HoverEvent.showText(TextUtility.INSTANCE.convertToComponent("&aClick to Copy!"))));
			return true;
		}

		Character character = Character.get(player);

		if (character == null) {
			player.sendMessage(
					TextUtility.INSTANCE.convertToComponent("&4You aren't on a character right now, so you cannot use /skin."));
			return false;
		}
		;

		if (args[0].equalsIgnoreCase("state")) {
			if (args.length < 2) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Usage: /skin state <id>"));
				return true;
			}
			int stateId;
			try {
				stateId = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Invalid state ID."));
				return true;
			}
			SkinStateManager mgr = SneakyCharacterManager.getInstance().skinStateManager;
			SkinState state = mgr.get(player.getUniqueId(), stateId);
			if (state == null) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Skin state not found."));
				return true;
			}
			if (!character.getKey().equals(state.characterKey())) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4That skin state belongs to a different character."));
				return true;
			}
			SkinState current = mgr.current(player.getUniqueId());
			if (current != null && current.id() == stateId) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4You are already on that skin state."));
				return true;
			}
			ProfileProperty stateProperty = new ProfileProperty("textures", state.texture(), state.signature());
			SkinUtil.applySkin(player, stateProperty);

			if (!state.proxyTextureUrl().equals(character.getSkin()) ||
					!state.proxySignature().equals(character.getSignature())) {
				character.setSkin(state.proxyTextureUrl());
				character.setTexture(state.proxyTexture());
				character.setSignature(state.proxySignature());
				CharacterUpdates.send(player, character.getKey(), CharacterUpdates.LEGACY_SKIN,
						state.proxyTextureUrl(), "", state.proxyTexture(), state.proxySignature(), character.isSlim());
			}

			mgr.activateState(player.getUniqueId(), stateId);
			return true;
		}

		if (args[0].equalsIgnoreCase("default") || args[0].equalsIgnoreCase("revert")) {
			CharacterSkinChangeEvent event = new CharacterSkinChangeEvent(player,
					SkinState.pendingCharacterSkinChange(character.getKey(), "default", null));
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) return false;

			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&eFetching skin.. Please wait..!"));
			resetPlayerSkin(player, character.getKey());
			return true;
		}

		String url = args[0];
		try {
			SkinFetching.policy().check(url);
		} catch (SkinFetchException e) {
			player.sendMessage(TextUtility.INSTANCE.convertToComponent(
					e.reason() == SkinFetchException.Reason.HOST_NOT_ALLOWED
							? "&4Skins from that site aren't allowed here."
							: "&4Invalid skin URL. Use a direct http(s) link to a 64x64 or 64x32 PNG."));
			return true;
		}

		Boolean slim = null;
		if (args.length > 1) {
			if (args[1].equalsIgnoreCase("slim"))
				slim = true;
			else
				slim = false;
		}

		CharacterSkinChangeEvent event = new CharacterSkinChangeEvent(player,
				SkinState.pendingCharacterSkinChange(character.getKey(), url, slim));
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return false;

		player.sendMessage(TextUtility.INSTANCE.convertToComponent("&aUpdating your skin!"));

		CharacterLoader.updateSkin(player, character.getKey(), url, slim, null);

		return true;
	}

	@Override
	public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args,
			Location location) {
		if (args.length == 1) {
			return Arrays.asList("revert", "fetch", "state");
		} else if (args.length == 2 && args[0].equalsIgnoreCase("fetch")
				&& sender.hasPermission(SneakyCharacterManager.IDENTIFIER + ".skinfetch.others")) {
			List<String> playerNames = new ArrayList<>();
			for (@NotNull
			Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase().startsWith(args[1].toLowerCase()) &&
						!player.getName().equals("CMI-Fake-Operator") &&
						!(SneakyCharacterManager.getInstance().papiActive
								&& PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null
								&& !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty()))
					playerNames.add(player.getName());
			}
			return playerNames;
		} else if (args.length == 2 && !args[0].equalsIgnoreCase("fetch")) {
			return Arrays.asList("slim", "classic");
		} else {
			return super.tabComplete(sender, alias, args, location); // or simply "return new ArrayList<>();"
		}
	}

	private void resetPlayerSkin(Player player, CharacterKey key) {
		Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
			try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
				URL url = new URL(
						String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false",
								player.getUniqueId().toString().replace("-", "")));
				HttpGet httpGet = new HttpGet(url.toURI());
				HttpResponse response = httpClient.execute(httpGet);
				if (response != null) {
					InputStream in = response.getEntity().getContent();
					JSONParser parser = new JSONParser();
					JSONObject result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
					if (result.containsKey("properties")) {

						List<JSONObject> properties = (List<JSONObject>) result.get("properties");

						JSONObject textureProp = properties.get(0);
						if (!((String) textureProp.get("name")).equalsIgnoreCase("textures"))
							throw new RuntimeException("That didn't work...");

						String textureValue = (String) textureProp.get("value");
						String signatureValue = (String) textureProp.get("signature");
						ProfileProperty property = new ProfileProperty("textures", textureValue, signatureValue);
						Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
							if (!player.isOnline()) return;
							PlayerProfile profile = player.getPlayerProfile();
							profile.setProperty(property);
							player.sendMessage(TextUtility.INSTANCE.convertToComponent("&aUpdating your skin!"));

							String textureURL = profile.getTextures().getSkin().toString();
							boolean isSlim = profile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

							// The skin belongs to the character /skin was run on, even if the player switched since.
							if (Character.isWearing(player, key)) {
								Character character = Character.get(player);
								character.setSkin(textureURL);
								character.setSlim(isSlim);
								character.setTexture(textureValue);
								character.setSignature(signatureValue);

								SkinUtil.applySkin(player, property);

								SneakyCharacterManager.getInstance().skinStateManager.record(
										player, "Regular", textureValue, signatureValue,
										key, textureURL, false);

								Entity vehicle = player.getVehicle();
								if (vehicle != null)
									vehicle.removePassenger(player);
								player.teleport(player.getLocation().add(0, 1, 0));
							} else {
								SneakyCharacterManager.getInstance().skinStateManager.forgetCharacter(player.getUniqueId(), key);
							}

							SkinCache.resolve(player, textureURL, isSlim).thenAccept(resolveResult -> {
								Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
									if (!player.isOnline()) return;
									String skinId = resolveResult.skinId;
									if (!skinId.isEmpty()) {
										SkinCache.register(player, skinId, textureURL, textureValue, signatureValue, textureURL);
										SkinCache.updateCharacterSkin(player, key, skinId, isSlim);
									} else {
										CharacterUpdates.send(player, key, CharacterUpdates.LEGACY_SKIN,
												textureURL, "", textureValue, signatureValue, isSlim);
									}
								});
							});
						});
					}
				}
			} catch (IOException | URISyntaxException | ParseException | RuntimeException exception) {
				exception.printStackTrace();
				Bukkit.getLogger().severe("Something went very wrong!");
			}
		});
	}

}
