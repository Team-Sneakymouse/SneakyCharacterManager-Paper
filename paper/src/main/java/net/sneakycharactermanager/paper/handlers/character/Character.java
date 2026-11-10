package net.sneakycharactermanager.paper.handlers.character;

import net.sneakycharactermanager.paper.api.character.CharacterKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.permissions.PermissionAttachmentInfo;

import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.util.ChatUtility;
import com.sneakyutils.util.InventoryUtility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A character, and when loaded, the body wearing it.
 *
 * <p>Identity rule: a character is its {@link CharacterKey} (owner + character ID). Its gameplay file and every
 * identity edit go to that key, never to the wearer, who may be another player. The only state keyed by the wearer is
 * "what this body wears" ({@link #get(Player)}) and the body-level exceptions listed in the README under
 * "Character identity" (nametags, placeholders, permissions, skin undo history, ...).
 */
public class Character {

	/** What each body is wearing. Keyed by the body on purpose; see the class comment. Main thread only. */
	private static Map<Player, Character> characterMap = new HashMap<>();
	/**
	 * The holder registry: the same pairs as {@link #characterMap}, by character. Changed only together with it
	 * ({@link #wear}, {@link #takeOff}). Concurrent so storage threads can read it
	 * ({@link HolderRegistry#heldAwayFromOwner}).
	 */
	private static final Map<CharacterKey, Player> holders = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Character>> playerCharactersCache = new ConcurrentHashMap<>();

	/** Owner + character ID: where this character's identity record and gameplay file live. */
	private final CharacterKey key;
	/** The body wearing this character, or null for a list entry, which is never loaded. */
	@Nullable private final Player player;
	private final String characterUUID;
	private String name;
	private String namePrefix = "";
	private String skin;
	private String skinUUID;
	private String texture;
	private String signature;
	private boolean slim;
	private JsonObject tags = null;
	private Gender gender = null;
	private String title = "";
	private final Map<String, String[]> uniformVariants = new HashMap<>();

	private boolean firstLoad = false;

	/**
	 * @param key    the character's owner and ID
	 * @param wearer the body that wears it once {@link #load()} runs, or null for a list entry
	 */
	public Character(CharacterKey key, @Nullable Player wearer, String characterName, String skin, String skinUUID,
			String texture, String signature, boolean slim, String tags, String gender, String title) {
		this.key = key;
		this.player = wearer;
		this.name = characterName;
		this.characterUUID = key.characterId();
		this.skin = skin;
		this.skinUUID = skinUUID;
		this.texture = texture;
		this.signature = signature;
		this.slim = slim;
		this.tags = tags.isEmpty() ? new JsonObject() : JsonParser.parseString(tags).getAsJsonObject();
		this.gender = Gender.fromString(gender);
		this.title = title == null ? "" : title;
	}

	/**
	 * Puts this character on its wearer, unless another body wears it, in which case the load is refused (see
	 * {@link HolderRegistry}). For other plugins; SCM's own loads say why they load with {@link #load(LoadReason)}.
	 */
	public void load() {
		load(LoadReason.OTHER);
	}

	/**
	 * Puts this character on its wearer: saves whatever the wearer had on, then applies this character's gameplay
	 * file. The file belongs to the owner ({@link GameplayFile#of}); a character that has no file yet gets one here.
	 * The holder rule runs first ({@link HolderRegistry}): if another body wears this character, {@code reason}
	 * decides whether the load is refused, goes to the selection handler, or takes the character back for its owner.
	 *
	 * @return true if the character is now on its wearer
	 */
	@ApiStatus.Internal
	public boolean load(LoadReason reason) {
		if (this.player == null) {
			SneakyCharacterManager.getInstance().getLogger().severe("Attempted to load character " + this.key + " without a wearer");
			return false;
		}
		if (!HolderRegistry.admit(this, reason)) return false;
		Character current = get(this.player);
		if (current != null) current.save();

		File characterFile = GameplayFile.of(this.key);
		boolean hasFile = GameplayFile.exists(characterFile); // sees the save just queued above, too
		this.firstLoad = !hasFile;
		// Only the owner's very first character on this server starts from the body's live state, as before
		// files moved to load time. Anyone else starts fresh: a borrowed character must not copy its wearer.
		boolean seedFromBody = this.firstLoad && current == null
				&& this.player.getUniqueId().equals(this.key.owner())
				&& !GameplayFile.ownerHasFiles(this.key)
				&& !SneakyCharacterManager.getInstance().getConfig().getBoolean("deleteCharacterDataOnServerStart", false);

		if (CharacterLoader.loadCharacter(this)) {
			SneakyCharacterManager.getInstance().skinQueue.updatePriority(this.player, this.key, SkinQueue.PRIO_LOAD);
			YamlConfiguration config;
			if (hasFile) {
				config = GameplayFile.read(characterFile);
			} else {
				config = seedFromBody ? snapshot() : GameplayFile.fresh(this.player.getInventory().getSize());
				GameplayFile.write(characterFile, config.saveToString());
			}

			if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageLocations", true)) {
				Location playerLocation = config.getLocation("location");
				if (playerLocation == null)
					playerLocation = this.player.getLocation(); // No config location? Leave where they are

				Entity vehicle = this.player.getVehicle();

				List<Entity> passengers = this.player.getPassengers();

				if (vehicle != null) {
					vehicle.removePassenger(this.player);
				}

				if (passengers.size() > 0) {
					for (Entity passenger : passengers) {
						if (passenger.getType() != EntityType.TEXT_DISPLAY) {
							this.player.removePassenger(passenger);
						}
					}
				}

				this.player.teleport(playerLocation.add(0, 1, 0));
			}

			if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
				if (config.getString("inventory") != null) {
					try {
						ItemStack[] inventoryContents = InventoryUtility.getSavedInventory(config.getString("inventory"));
						this.player.getInventory().setContents(inventoryContents);
					} catch (IOException e) {
						SneakyCharacterManager.getInstance().getLogger().warning(
								"Failed to load inventory for character " + this.characterUUID + ": " + e.getMessage());
					}
				}
			}

			if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageAttributes", false)) {
				applyAttributes(config);
			}

			wear(this.player, this);
			// Only now does Character.get(body) return this character, so the plate shows its title, not the previous one's.
			SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(this.player, getDisplayName());
			ConsoleCommandCharDisable.playerCharEnable(this.player.getUniqueId().toString());
			return true;
		}
		return false;
	}

	public Player getPlayer() {
		return this.player;
	}

	public String getCharacterUUID() {
		return this.characterUUID;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNamePrefix() {
		return this.namePrefix;
	}

	public void setNamePrefix(String namePrefix) {
		this.namePrefix = namePrefix == null ? "" : namePrefix;
	}

	public String getDisplayName() {
		return (this.namePrefix == null ? "" : this.namePrefix) + this.name + getGenderSuffix();
	}

	public String getNameUnformatted() {
		return ChatUtility.stripFormatting(this.name);
	}

	public String getSkin() {
		return this.skin;
	}

	public void setSkin(String skin) {
		this.skin = skin;
	}

	public String getSkinUUID() {
		return this.skinUUID;
	}

	public void setSkinUUID(String skinUUID) {
		this.skinUUID = skinUUID;
	}

	public String getTexture() {
		return texture;
	}

	public void setTexture(String texture) {
		this.texture = texture;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public boolean isSlim() {
		return this.slim;
	}

	public JsonObject getTags() {
		return this.tags;
	}

	public String getTagsAsString() {
		return this.tags.isEmpty() ? "{}" : this.tags.toString();
	}

	public String tagValue(String key) {
		return this.tags.get(key) != null ? this.tags.get(key).getAsJsonPrimitive().getAsString() : "";
	}

	public Boolean hasTag(String key) {
		return this.tags.keySet().contains(key);
	}

	public Gender getGender() {
		return this.gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public String getTitle() {
		return this.title == null ? "" : this.title;
	}

	public void setTitle(String title) {
		this.title = title == null ? "" : title;
	}

	public void addUniformVariant(String hash, String uuid, String url, String texture, String signature) {
		this.uniformVariants.put(hash, new String[]{uuid, url, texture, signature});
	}

	public String[] getUniformVariant(String hash) {
		return this.uniformVariants.get(hash);
	}

	public void clearUniformVariants() {
		this.uniformVariants.clear();
	}

	public Map<String, String[]> getUniformVariants() {
		return this.uniformVariants;
	}

	public String getGenderSuffix() {
		if (this.gender == null) return "";
		String path = "gender-suffixes." + this.gender.toConfigKey();
		return SneakyCharacterManager.getInstance().getConfig().getString(path, "");
	}

	private List<Map<String, Object>> serializeAttributes() {
		List<Map<String, Object>> serialized = new ArrayList<>();

		for (Attribute attribute : Registry.ATTRIBUTE) {
			AttributeInstance instance = this.player.getAttribute(attribute);
			if (instance == null)
				continue;

			double defaultValue = defaultFor(attribute, instance);
			double baseValue = instance.getBaseValue();
			Collection<AttributeModifier> modifiers = instance.getModifiers();
			if (modifiers == null) continue;

			boolean hasModifiers = modifiers != null && !modifiers.isEmpty();
			boolean baseDiffers = Double.compare(baseValue, defaultValue) != 0;

			if (!baseDiffers && !hasModifiers)
				continue; // only store non-default attributes

			Map<String, Object> data = new HashMap<>();
			data.put("key", attribute.getKey().toString());
			data.put("base", baseValue);

			if (hasModifiers) {
				List<Map<String, Object>> modifierList = new ArrayList<>();
				Collection<AttributeModifier> typeDefaults = getTypeDefaultModifiers(attribute);
				for (AttributeModifier modifier : modifiers) {
					if (isVanillaModifier(modifier) || typeDefaults.contains(modifier)) continue; // skip vanilla/type defaults
					Map<String, Object> modMap = new HashMap<>();
					NamespacedKey modKey = modifier.getKey();
					if (modKey != null) {
						modMap.put("key", modKey.toString());
					} else {
						continue;
					}
					modMap.put("name", modifier.getName());
					modMap.put("amount", modifier.getAmount());
					modMap.put("operation", modifier.getOperation().name());
					if (modifier.getSlotGroup() != null) {
						modMap.put("slot", modifier.getSlotGroup().toString());
					}
					modifierList.add(modMap);
				}
				data.put("modifiers", modifierList);
			}

			serialized.add(data);
		}

		return serialized;
	}

	private void applyAttributes(YamlConfiguration config) {
		// Always reset to defaults to avoid leaking values across characters
		resetAttributesToDefaults();

		if (!config.contains("attributes"))
			return;

		List<Map<?, ?>> attributes = config.getMapList("attributes");
		for (Map<?, ?> entry : attributes) {
			try {
				String key = entry.get("key").toString();
				Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.fromString(key));
				if (attribute == null)
					continue;
				AttributeInstance instance = this.player.getAttribute(attribute);
				if (instance == null)
					continue;

				if (entry.containsKey("base")) {
					double base = Double.parseDouble(entry.get("base").toString());
					instance.setBaseValue(base);
				}

				if (entry.containsKey("modifiers")) {
					List<?> modListRaw = (List<?>) entry.get("modifiers");
					for (Object modObj : modListRaw) {
						if (!(modObj instanceof Map))
							continue;
						Map<?, ?> mod = (Map<?, ?>) modObj;
						double amount = Double.parseDouble(mod.get("amount").toString());
						AttributeModifier.Operation operation = AttributeModifier.Operation
								.valueOf(mod.get("operation").toString());

						NamespacedKey modKey = mod.containsKey("key") ? NamespacedKey.fromString(mod.get("key").toString()) : null;
						if (modKey == null) continue; // ignore legacy/unknown modifiers

						AttributeModifier modifier;
						if (mod.containsKey("slot")) {
							String slotName = mod.get("slot").toString();
							EquipmentSlotGroup slot = EquipmentSlotGroup.getByName(slotName);
							if (slot != null) {
								modifier = new AttributeModifier(modKey, amount, operation, slot);
							} else {
								modifier = new AttributeModifier(modKey, amount, operation);
							}
						} else {
							modifier = new AttributeModifier(modKey, amount, operation);
						}

						instance.addModifier(modifier);
					}
				}
			} catch (Exception e) {
				SneakyCharacterManager.getInstance().getLogger()
						.warning("Failed to apply attributes for a character entry: " + e.getMessage());
			}
		}
	}

	private void resetAttributesToDefaults() {
		for (Attribute attribute : Registry.ATTRIBUTE) {
			AttributeInstance instance = this.player.getAttribute(attribute);
			if (instance == null) continue;

			for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
				instance.removeModifier(modifier);
			}
			instance.setBaseValue(defaultFor(attribute, instance));
			for (AttributeModifier mod : getTypeDefaultModifiers(attribute)) {
				instance.addModifier(mod);
			}
		}
	}

	private boolean isVanillaModifier(AttributeModifier modifier) {
		NamespacedKey key = modifier.getKey();
		return key != null && "minecraft".equals(key.getNamespace());
	}

	private double defaultFor(Attribute attribute, AttributeInstance instance) {
		NamespacedKey key = attribute.getKey();
		String keyString = key != null ? key.toString().toLowerCase() : "";
		if (keyString.contains("movement_speed")) return 0.1D;
		if (keyString.contains("attack_damage")) return 1.0D;

		// Fallback for runtimes where getKey may be null
		String asString = attribute.toString().toLowerCase();
		if (asString.contains("movement_speed")) return 0.1D;
		if (asString.contains("attack_damage")) return 1.0D;

		return instance.getDefaultValue();
	}

	private Collection<AttributeModifier> getTypeDefaultModifiers(Attribute attribute) {
		return List.of();
	}

	public void setSlim(boolean slim) {
		this.slim = slim;
	}

	public void setFirstLoad(boolean firstLoad) {
		this.firstLoad = firstLoad;
	}

	public boolean isFirstLoad() {
		return this.firstLoad;
	}

	/** Writes the wearer's live location, inventory and attributes to this character's (the owner's) file. */
	public void save() {
		if (this.player == null) return;
		GameplayFile.write(GameplayFile.of(this.key), snapshot().saveToString());
	}

	/** The wearer's live gameplay state, in the gameplay file's format. */
	private YamlConfiguration snapshot() {
		YamlConfiguration config = new YamlConfiguration();

		Location playerLocation = this.player.getLocation();
		config.set("location", playerLocation);

		if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
			String inventoryYaml = InventoryUtility.inventoryToPaperYaml(this.player.getInventory());
			config.set("inventory", inventoryYaml);
		}

		if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageAttributes", false)) {
			config.set("attributes", serializeAttributes());
		}
		return config;
	}

	public CharacterKey getKey() {
		return this.key;
	}

	public UUID getOwnerUUID() {
		return this.key.owner();
	}

	@Nullable
	public static Character get(Player player) {
		return characterMap.get(player);
	}

	public static Collection<Character> getAll() {
		return characterMap.values();
	}

	public static void remove(Player player) {
		takeOff(player);
		playerCharactersCache.remove(player.getUniqueId());
	}

	private static void wear(Player body, Character character) {
		Character previous = characterMap.put(body, character);
		if (previous != null) holders.remove(previous.key, body);
		holders.put(character.key, body);
	}

	/** {@code body} stops wearing its character, without saving it. */
	static void takeOff(Player body) {
		Character previous = characterMap.remove(body);
		if (previous != null) holders.remove(previous.key, body);
	}

	public static List<Character> getPlayerCharacters(UUID playerUUID) {
		return playerCharactersCache.getOrDefault(playerUUID, new ArrayList<>());
	}

	public static void updatePlayerCharacters(UUID playerUUID, List<Character> characters) {
		playerCharactersCache.put(playerUUID, new ArrayList<>(characters));
	}

	public static void saveAll() {
		for (Player player : new ArrayList<>(characterMap.keySet())) {
			if (player.isOnline()) {
				Character character = get(player);
				if (character != null)
					character.save();
			} else {
				SneakyCharacterManager.getInstance().getLogger().severe(
						"SneakyCharacterManager found an offline player on the characterMap. They have been removed, but this should never happen: "
								+ player.getName());
				takeOff(player);
			}
		}
	}

	public static boolean isPlayedMapped(Player player) {
		return characterMap.containsKey(player);
	}

	/**
	 * True if {@code body} is wearing exactly this character (same owner and ID) right now.
	 *
	 * <p>This is the guard for deferred work: anything that finishes later (async result, open menu or dialog, chat
	 * button) keeps the key it started for, writes only to that key, and touches the body or its worn character only
	 * while this returns true. An offline body wears nothing.
	 */
	public static boolean isWearing(Player body, CharacterKey key) {
		Character worn = characterMap.get(body);
		return worn != null && worn.key.equals(key);
	}

	/**
	 * Resolves a bare character ID against what {@code body} is wearing: the worn character's key if the IDs match,
	 * otherwise null. Only for old entry points that pass an ID without an owner; never guess the owner from the body.
	 */
	@Nullable
	public static CharacterKey wornKey(Player body, String characterId) {
		Character worn = characterMap.get(body);
		return worn != null && worn.characterUUID.equals(characterId) ? worn.key : null;
	}

	/**
	 * The body wearing this character right now, or null. This is the holder registry ({@link HolderRegistry});
	 * safe to read from any thread, but only the main thread's answer is current.
	 */
	@Nullable
	public static Player wearerOf(CharacterKey key) {
		return holders.get(key);
	}

	public static boolean canPlayerLoadCharacter(Player player, String characterUUID) {
		if (player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".admin.bypass.characterlocks"))
			return true;

		Boolean characterPerm = null;
		for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
			if (permission.getPermission().equals(SneakyCharacterManager.IDENTIFIER + ".character." + characterUUID)) {
				characterPerm = permission.getValue();
			}
		}

		if ((characterPerm == null && !player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".character.*")) ||
				(characterPerm != null && !characterPerm)) {
			return false;
		}

		return true;
	}
}
