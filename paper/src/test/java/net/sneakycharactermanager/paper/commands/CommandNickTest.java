package net.sneakycharactermanager.paper.commands;

import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.CharacterNickChangeEvent;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandNickTest {
    static class CapturingCommand extends CommandNick {
        String draft;
        String error;
        String characterId;
        void openForm(Player player, String characterId, String draft, String error) {
            this.characterId = characterId;
            this.draft = draft;
            this.error = error;
        }
    }

    @Test
    void nativeLengthLimitHonorsPermissionWithoutTruncatingRetry() {
        assertEquals(32, CommandNick.inputMaxLength(false, "Short name"));
        assertEquals(Integer.MAX_VALUE, CommandNick.inputMaxLength(true, "Short name"));
        assertEquals(100, CommandNick.inputMaxLength(false, "x".repeat(100)));
    }

    static Stream<Arguments> nicknames() {
        return Stream.of(
                Arguments.of("a".repeat(32), false, null),
                Arguments.of("a".repeat(33), false, "32"),
                Arguments.of("a".repeat(100), true, null),
                Arguments.of("  Áé čď ÆØ O'Name \"The-First\"  ", false, null),
                Arguments.of("Name_123", false, null),
                Arguments.of("Bad!", false, "special"),
                Arguments.of("<red>Name", false, "special"),
                Arguments.of("Name&c", false, "special"),
                Arguments.of("<red>Name!&c", true, null),
                Arguments.of("PrefixBaNnEdSuffix", false, "banned"),
                Arguments.of("PrefixBaNnEdSuffix", true, "banned"));
    }

    @ParameterizedTest
    @MethodSource("nicknames")
    void legacyArgumentsKeepValidationAndMutation(String raw, boolean format, String failure) {
        try (Fixture f = new Fixture(format)) {
            boolean result = f.command.execute(f.player, "nick", raw.split(" ", -1));
            assertEquals(failure == null, result);
            f.verifyMutation(raw, failure == null);
            assertNull(f.command.draft, "Arguments must not open a dialog");
        }
    }

    @ParameterizedTest
    @MethodSource("nicknames")
    void dialogUsesSameRulesWithoutNormalizingInput(String raw, boolean format, String failure) {
        try (Fixture f = new Fixture(format)) {
            f.command.handleSubmit(f.player, "character-id", raw);
            f.verifyMutation(raw, failure == null);
            if (failure != null) {
                assertEquals(raw, f.command.draft);
                assertTrue(f.command.error.contains(failure), f.command.error);
            } else assertNull(f.command.draft);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
    void clearedFieldReopensWithoutSavingBlankName(String raw) {
        try (Fixture f = new Fixture(false)) {
            f.command.handleSubmit(f.player, "character-id", raw);
            assertEquals(raw == null ? "" : raw, f.command.draft);
            assertTrue(f.command.error.contains("empty"));
            f.verifyMutation(raw, false);
        }
    }

    @Test
    void cancelledEventReopensWithoutMutation() {
        try (Fixture f = new Fixture(false)) {
            doAnswer(invocation -> {
                ((CharacterNickChangeEvent) invocation.getArgument(0)).setCancelled(true);
                return null;
            }).when(f.events).callEvent(any());
            f.command.handleSubmit(f.player, "character-id", "New name");
            assertEquals("New name", f.command.draft);
            assertTrue(f.command.error.contains("cancelled"));
            f.verifyMutation("New name", false);
        }
    }

    @Test
    void cancelledLegacyCommandKeepsSilentFailure() {
        try (Fixture f = new Fixture(false)) {
            doAnswer(invocation -> {
                ((CharacterNickChangeEvent) invocation.getArgument(0)).setCancelled(true);
                return null;
            }).when(f.events).callEvent(any());
            assertFalse(f.command.execute(f.player, "nick", new String[]{"New", "name"}));
            verify(f.player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
            assertNull(f.command.draft);
            f.verifyMutation("New name", false);
        }
    }

    static class Fixture implements AutoCloseable {
        final Player player = mock(Player.class);
        final Character character = mock(Character.class);
        final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        final PluginManager events = mock(PluginManager.class);
        final CapturingCommand command = new CapturingCommand();
        final MockedStatic<ConsoleCommandCharTemp> temps = mockStatic(ConsoleCommandCharTemp.class);
        final MockedStatic<Character> characters = mockStatic(Character.class);
        final MockedStatic<SneakyCharacterManager> plugins = mockStatic(SneakyCharacterManager.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class);

        Fixture(boolean format) {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.hasPermission("sneakycharacters.command.nick")).thenReturn(true);
            when(player.hasPermission("sneakycharacters.formatnames")).thenReturn(format);
            when(character.getCharacterUUID()).thenReturn("character-id");
            when(character.getName()).thenReturn("Old name");
            when(character.getDisplayName()).thenReturn("Rendered name");
            characters.when(() -> Character.get(player)).thenReturn(character);
            plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
            YamlConfiguration config = new YamlConfiguration();
            config.set("bannedWords", List.of("banned"));
            when(plugin.getConfig()).thenReturn(config);
            plugin.nametagManager = mock(NametagManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        }

        void verifyMutation(String raw, boolean success) {
            if (success) {
                verify(character).setName(raw);
                verify(plugin.nametagManager).nicknamePlayer(player, "Rendered name");
                proxy.verify(() -> ProxyMessagingUtil.sendByteArray(player, "updateCharacter",
                        player.getUniqueId().toString(), "character-id", 2, raw));
                var event = org.mockito.ArgumentCaptor.forClass(CharacterNickChangeEvent.class);
                verify(events).callEvent(event.capture());
                assertEquals(raw, event.getValue().getNewName());
            } else {
                verify(character, never()).setName(anyString());
                verifyNoInteractions(plugin.nametagManager);
                proxy.verifyNoInteractions();
            }
        }

        public void close() {
            proxy.close(); bukkit.close(); plugins.close(); characters.close(); temps.close();
        }
    }

    @Test
    void invalidSubmissionReopensWithExactDraftAndExplanation() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("sneakycharacters.command.nick")).thenReturn(true);
        Character character = mock(Character.class);
        when(character.getCharacterUUID()).thenReturn("character-id");
        String raw = "  " + "a".repeat(33) + "  ";
        try (var temps = mockStatic(ConsoleCommandCharTemp.class);
             var characters = mockStatic(Character.class)) {
            characters.when(() -> Character.get(player)).thenReturn(character);
            CapturingCommand command = new CapturingCommand();
            command.handleSubmit(player, "character-id", raw);
            assertEquals(raw, command.draft);
            assertTrue(command.error.contains("32"));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"permission", "template", "missing", "switched"})
    void submissionRechecksContext(String state) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("sneakycharacters.command.nick")).thenReturn(!state.equals("permission"));
        Character character = mock(Character.class);
        when(character.getCharacterUUID()).thenReturn(state.equals("switched") ? "other-id" : "character-id");
        String raw = "x".repeat(33);
        try (var temps = mockStatic(ConsoleCommandCharTemp.class);
             var characters = mockStatic(Character.class)) {
            temps.when(() -> ConsoleCommandCharTemp.isPlayerTempChar(player.getUniqueId().toString()))
                    .thenReturn(state.equals("template"));
            characters.when(() -> Character.get(player)).thenReturn(state.equals("missing") ? null : character);
            CapturingCommand command = new CapturingCommand();
            command.handleSubmit(player, "character-id", raw);
            assertEquals(raw, command.draft);
            String expected = switch (state) {
                case "permission" -> "permission";
                case "template" -> "template";
                case "missing" -> "retrieve character";
                default -> "changed";
            };
            assertTrue(command.error.contains(expected), command.error);
            verify(character, never()).setName(anyString());
        }
    }

    @Test
    void zeroArgumentsOpensNativeDialog() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(true);
        Character character = mock(Character.class);
        when(character.getCharacterUUID()).thenReturn("character-id");
        when(character.getName()).thenReturn("Current name");
        try (var temps = mockStatic(ConsoleCommandCharTemp.class);
             var characters = mockStatic(Character.class)) {
            characters.when(() -> Character.get(player)).thenReturn(character);
            CapturingCommand command = new CapturingCommand();
            assertTrue(command.execute(player, "nick", new String[0]));
            assertEquals("Current name", command.draft);
            assertEquals("character-id", command.characterId);
        }
    }
}
