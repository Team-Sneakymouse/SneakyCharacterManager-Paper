package net.sneakycharactermanager.paper.handlers.character;

import java.io.ByteArrayOutputStream;
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

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import net.sneakycharactermanager.paper.util.InventoryUtility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Character {

	private static Map<Player, Character> characterMap = new HashMap<>();

	private final Player player;
	private String characterUUID;
	private String name;
	private String namePrefix = "";
	private String skin;
	private String skinUUID;
	private boolean slim;
	private JsonObject tags = null;
	private Gender gender = null;

	private boolean firstLoad = false;

	public Character(String playerUUID, String characterUUID, String characterName, String skin, String skinUUID, boolean slim,
			String tags, String gender) {
		this.player = Bukkit.getPlayer(UUID.fromString(playerUUID));
		this.name = characterName;
		this.characterUUID = characterUUID;
		this.skin = skin;
		this.skinUUID = skinUUID;
		this.slim = slim;
		this.tags = tags.isEmpty() ? new JsonObject() : JsonParser.parseString(tags).getAsJsonObject();
		this.gender = Gender.fromString(gender);

		File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID);
		boolean firstLogin = false;

		if (!playerDir.exists()) {
			firstLogin = true;
			playerDir.mkdirs();
		}

		File characterFile = new File(playerDir, this.characterUUID + ".yml");

		if (!characterFile.exists()) {
			this.firstLoad = true;
			if (firstLogin && !SneakyCharacterManager.getInstance().getConfig()
					.getBoolean("deleteCharacterDataOnServerStart", false)) {
				this.save();
			} else {
				YamlConfiguration config = new YamlConfiguration();

				config.set("location", Bukkit.getWorlds().get(0).getSpawnLocation());

				try {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

					dataOutput.writeInt(this.player.getInventory().getSize());

					for (int i = 0; i < this.player.getInventory().getSize(); i++) {
						dataOutput.writeObject(new ItemStack(Material.AIR));
					}

					dataOutput.close();
					config.set("inventory", Base64Coder.encodeLines(outputStream.toByteArray()));

					// Converts the inventory and its contents to base64, This also saves item
					// meta-data and inventory type
				} catch (Exception e) {
					throw new IllegalStateException("Could not convert inventory to base64.", e);
				}

				try {
					config.save(characterFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void load() {
		if (this.player != null && characterMap.containsKey(this.player)) {
			Character character = get(this.player);
			if (character != null)
				character.save();
		}

		File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(),
				this.player.getUniqueId().toString());
		File characterFile = new File(playerDir, this.characterUUID + ".yml");

		if (!characterFile.exists()) {
			SneakyCharacterManager.getInstance().getLogger()
					.severe("SneakyCharacterManager attempted to load character data from a file that does not exist: "
							+ this.characterUUID);
			return;
		}

		if (CharacterLoader.loadCharacter(this)) {
			YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);

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
					ItemStack[] inventoryContents = InventoryUtility.getSavedInventory(config.getString("inventory"));
					this.player.getInventory().setContents(inventoryContents);
				}
			}

			if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageAttributes", false)) {
				applyAttributes(config);
			}

			characterMap.put(this.player, this);
			ConsoleCommandCharDisable.playerCharEnable(this.player.getUniqueId().toString());
		}

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
		Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
		Matcher matcher = pattern.matcher(this.name);
		return matcher.replaceAll("");
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

	public void save() {
		File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(),
				this.player.getUniqueId().toString());

		if (!playerDir.exists()) {
			playerDir.mkdirs();
		}

		File characterFile = new File(playerDir, this.characterUUID + ".yml");

		YamlConfiguration config = new YamlConfiguration();

		Location playerLocation = this.player.getLocation();
		config.set("location", playerLocation);

		if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
			String inventoryB64 = InventoryUtility.inventoryToBase64(this.player.getInventory());
			config.set("inventory", inventoryB64);
		}

		if (SneakyCharacterManager.getInstance().getConfig().getBoolean("manageAttributes", false)) {
			config.set("attributes", serializeAttributes());
		}

		try {
			config.save(characterFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Nullable
	public static Character get(Player player) {
		return characterMap.get(player);
	}

	public static Collection<Character> getAll() {
		return characterMap.values();
	}

	public static void remove(Player player) {
		characterMap.remove(player);
	}

	public static void saveAll() {
		for (Player player : characterMap.keySet()) {
			if (player.isOnline()) {
				Character character = get(player);
				if (character != null)
					character.save();
			} else {
				SneakyCharacterManager.getInstance().getLogger().severe(
						"SneakyCharacterManager found an offline player on the characterMap. They have been removed, but this should never happen: "
								+ player.getName());
				characterMap.remove(player);
			}
		}
	}

	public static boolean isPlayedMapped(Player player) {
		return characterMap.containsKey(player);
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
