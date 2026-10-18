package net.sneakycharactermanager.paper.handlers.titles;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Loads and persists title key → display text from {@code titles.yml}.
 */
public final class TitleRegistry {

	public static final String TITLE_PERMISSION_PREFIX = SneakyCharacterManager.IDENTIFIER + ".title.";

	private final Map<String, String> titles = new LinkedHashMap<>();
	private File file;

	public void load(@NotNull SneakyCharacterManager plugin) {
		plugin.saveResource("titles.yml", false);
		this.file = new File(plugin.getDataFolder(), "titles.yml");
		reload();
	}

	public synchronized void reload() {
		titles.clear();
		if (file == null || !file.exists()) {
			return;
		}
		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
		for (String key : config.getKeys(false)) {
			String normalized = normalizeTitleKey(key);
			if (normalized.isEmpty()) {
				continue;
			}
			String display = config.getString(key);
			if (display == null || display.isBlank()) {
				continue;
			}
			titles.put(normalized, display.trim());
		}
	}

	public synchronized @NotNull Map<String, String> all() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(titles));
	}

	public synchronized boolean contains(@NotNull String key) {
		return titles.containsKey(normalizeTitleKey(key));
	}

	public synchronized @Nullable String getDisplay(@Nullable String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		return titles.get(normalizeTitleKey(key));
	}

	/**
	 * Inserts a new title. Returns false if the key is empty or already exists.
	 */
	public synchronized boolean insert(@NotNull String rawKey, @NotNull String display) {
		String key = normalizeTitleKey(rawKey);
		String trimmedDisplay = display == null ? "" : display.trim();
		if (key.isEmpty() || trimmedDisplay.isEmpty()) {
			return false;
		}
		if (titles.containsKey(key)) {
			return false;
		}
		titles.put(key, trimmedDisplay);
		save();
		return true;
	}

	private synchronized void save() {
		if (file == null) {
			return;
		}
		YamlConfiguration config = new YamlConfiguration();
		for (Map.Entry<String, String> entry : titles.entrySet()) {
			config.set(entry.getKey(), entry.getValue());
		}
		try {
			config.save(file);
		} catch (IOException e) {
			SneakyCharacterManager.getInstance().getLogger().severe("Failed to save titles.yml: " + e.getMessage());
		}
	}

	/**
	 * Normalize a title code: trim, lowercase, spaces/hyphens to underscores,
	 * strip non {@code [a-z0-9_]}, collapse and trim underscores.
	 */
	public static @NotNull String normalizeTitleKey(@Nullable String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim().toLowerCase()
				.replace(' ', '_')
				.replace('-', '_');
		StringBuilder out = new StringBuilder(s.length());
		boolean lastUnderscore = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
				lastUnderscore = false;
			} else if (c == '_') {
				if (!lastUnderscore && out.length() > 0) {
					out.append('_');
					lastUnderscore = true;
				}
			}
		}
		while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
			out.setLength(out.length() - 1);
		}
		return out.toString();
	}

	public static @NotNull String permissionNode(@NotNull String key) {
		return TITLE_PERMISSION_PREFIX + Objects.requireNonNull(normalizeTitleKey(key));
	}
}
