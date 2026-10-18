package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.common.titles.TitleKeys;
import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.YamlFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and persists title key → display text from proxy {@code titles.yml}.
 */
public final class TitleCatalog {

	private final File file;
	private final ProxyLogger logger;
	private final Map<String, String> titles = new LinkedHashMap<>();

	public TitleCatalog(File dataFolder, ProxyLogger logger) {
		this.file = new File(dataFolder, "titles.yml");
		this.logger = logger;
		load();
	}

	public synchronized void load() {
		Map<String, Object> root = YamlFiles.load(file, logger);
		if (root == null) {
			return;
		}
		titles.clear();
		for (Map.Entry<String, Object> entry : root.entrySet()) {
			String key = TitleKeys.normalize(entry.getKey());
			if (key.isEmpty() || entry.getValue() == null) {
				continue;
			}
			String display = String.valueOf(entry.getValue()).trim();
			if (display.isEmpty()) {
				continue;
			}
			titles.put(key, display);
		}
	}

	public synchronized Map<String, String> all() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(titles));
	}

	public synchronized boolean contains(String key) {
		return titles.containsKey(TitleKeys.normalize(key));
	}

	/**
	 * Inserts a new title. Returns false if the key is empty, display is empty, or key already exists.
	 */
	public synchronized boolean insert(String rawKey, String display) {
		String key = TitleKeys.normalize(rawKey);
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

	/**
	 * Flat wire format: {@code [key1, display1, key2, display2, ...]}.
	 */
	public synchronized List<String> wirePairs() {
		List<String> pairs = new ArrayList<>(titles.size() * 2);
		for (Map.Entry<String, String> entry : titles.entrySet()) {
			pairs.add(entry.getKey());
			pairs.add(entry.getValue());
		}
		return pairs;
	}

	private synchronized void save() {
		Map<String, Object> root = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : titles.entrySet()) {
			root.put(entry.getKey(), entry.getValue());
		}
		YamlFiles.save(file, root, logger);
	}
}
