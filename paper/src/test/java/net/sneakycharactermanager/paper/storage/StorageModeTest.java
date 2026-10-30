package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageModeTest {
    @Test void missingAndExistingConfigurationRemainLegacy() {
        assertEquals(StorageMode.LEGACY, StorageMode.parse(null));
        assertEquals(StorageMode.LEGACY, StorageMode.parse("legacy"));
    }
    @Test void databaseIsExplicitAndUnknownModesFailClosed() {
        assertEquals(StorageMode.SNEAKYDB, StorageMode.parse("sneakydb"));
        assertThrows(IllegalArgumentException.class, () -> StorageMode.parse("sneakybd"));
    }
}
