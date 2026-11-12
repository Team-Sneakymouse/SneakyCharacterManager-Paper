package net.sneakycharactermanager.paper.storage;

import net.sneakycharactermanager.paper.api.directory.CharacterInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class CharacterDirectoryTest {
    static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    final Logger logger = Logger.getAnonymousLogger();

    @Test void wireEntriesBecomePlainNamedInfosAndNonUuidsAreSkipped() {
        List<CharacterInfo> infos = DirectoryResults.fromWire(List.of(
                A.toString(), OWNER.toString(), "<gold>Monica</gold> &lRupert", "true",
                "guard", OWNER.toString(), "Guard", "true",
                B.toString(), "template", "Template", "true",
                B.toString(), OWNER.toString(), "Gone", "false"));
        assertEquals(List.of(new CharacterInfo(A, OWNER, "Monica Rupert", true), new CharacterInfo(B, OWNER, "Gone", false)), infos);
        assertEquals(List.of(new CharacterInfo(A, OWNER, "Monica Rupert", true)), DirectoryResults.enabled(infos));
        assertThrows(IllegalStateException.class, () -> DirectoryResults.fromWire(List.of("a", "b", "c")));
    }
    @Test void singleRefusesToGuessBetweenDuplicates() {
        CharacterInfo one = new CharacterInfo(A, OWNER, "One", false);
        assertEquals(Optional.of(one), DirectoryResults.single(A, List.of(one), logger));
        assertEquals(Optional.empty(), DirectoryResults.single(A, List.of(), logger));
        assertEquals(Optional.empty(), DirectoryResults.single(A, List.of(one, new CharacterInfo(A, B, "Two", true)), logger));
    }
    @Test void sqlDirectoryQueriesInMenuOrderAndFiltersDisabled() {
        SqlStoreTest.Api api = new SqlStoreTest.Api(); api.ready.complete(null);
        SqlCharacterDirectory directory = new SqlCharacterDirectory(new SqlStore(api, 5), logger);
        api.rows = List.of(row(A, "&aMouse", true), row(B, "Gone", false));
        assertEquals(List.of(new CharacterInfo(A, OWNER, "Mouse", true)), directory.charactersOf(OWNER).join());
        String sql = api.statements.getLast();
        assertTrue(sql.contains("WHERE owner_id = ? ORDER BY ordinal"), sql);
        assertEquals(List.of(OWNER.toString()), api.bindings.getLast());

        api.rows = List.of(row(B, "Gone", false));
        assertEquals(Optional.of(new CharacterInfo(B, OWNER, "Gone", false)), directory.character(B).join());
        assertTrue(api.statements.getLast().contains("owner_id <> 'template' LIMIT 2"));
        assertEquals(List.of(B.toString()), api.bindings.getLast());
    }
    private static Map<String,Object> row(UUID id, String name, boolean enabled) {
        return Map.of("character_id", id.toString(), "owner_id", OWNER.toString(), "name", name, "enabled", enabled);
    }
}
