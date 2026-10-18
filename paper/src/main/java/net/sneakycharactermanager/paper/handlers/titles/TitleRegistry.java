package net.sneakycharactermanager.paper.handlers.titles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.common.titles.TitleKeys;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * In-memory title key → display text cache, synced from the proxy {@code titles.yml}.
 */
public final class TitleRegistry {

	public static final String TITLE_PERMISSION_PREFIX = SneakyCharacterManager.IDENTIFIER + ".title.";

	private final Map<String, String> titles = new LinkedHashMap<>();

	public synchronized void replaceAll(@NotNull Map<String, String> next) {
		titles.clear();
		if (next == null) {
			return;
		}
		for (Map.Entry<String, String> entry : next.entrySet()) {
			String key = normalizeTitleKey(entry.getKey());
			if (key.isEmpty()) {
				continue;
			}
			String display = entry.getValue() == null ? "" : entry.getValue().trim();
			if (display.isEmpty()) {
				continue;
			}
			titles.put(key, display);
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

	public static @NotNull String normalizeTitleKey(@Nullable String raw) {
		return TitleKeys.normalize(raw);
	}

	public static @NotNull String permissionNode(@NotNull String key) {
		return TITLE_PERMISSION_PREFIX + Objects.requireNonNull(normalizeTitleKey(key));
	}
}
