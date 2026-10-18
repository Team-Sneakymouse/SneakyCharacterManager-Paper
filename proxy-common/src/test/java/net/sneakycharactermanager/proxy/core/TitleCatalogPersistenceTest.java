package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleCatalogPersistenceTest {

	@TempDir
	File dataFolder;

	@Test
	void insertSavesAndReloads() {
		ProxyLogger logger = silentLogger();
		TitleCatalog catalog = new TitleCatalog(dataFolder, logger);
		assertTrue(catalog.insert("Master Fisherman", "Master Fisherman"));
		assertFalse(catalog.insert("master_fisherman", "Duplicate"));

		File file = new File(dataFolder, "titles.yml");
		Map<String, Object> saved = YamlFiles.load(file, logger);
		assertEquals("Master Fisherman", String.valueOf(saved.get("master_fisherman")));

		TitleCatalog reloaded = new TitleCatalog(dataFolder, logger);
		assertTrue(reloaded.contains("master_fisherman"));
		assertEquals("Master Fisherman", reloaded.all().get("master_fisherman"));
		assertEquals(List.of("master_fisherman", "Master Fisherman"), reloaded.wirePairs());
	}

	private static ProxyLogger silentLogger() {
		return new ProxyLogger() {
			@Override public void info(String message) {}
			@Override public void warning(String message) {}
			@Override public void severe(String message) {}
			@Override public void severe(String message, Throwable throwable) {}
		};
	}
}
