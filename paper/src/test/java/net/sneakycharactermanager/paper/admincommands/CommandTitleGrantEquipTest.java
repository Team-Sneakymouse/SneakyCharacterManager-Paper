package net.sneakycharactermanager.paper.admincommands;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.titles.TitlePermissionGranter;
import net.sneakycharactermanager.paper.handlers.titles.TitleSelectionMenu;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/**
 * /titlegrant captures the target's character when the admin submits; the proxy answers later, and the title is only
 * equipped if the target still wears that character. The permission is account-wide and is granted either way.
 */
class CommandTitleGrantEquipTest {
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final MockedStatic<Character> characters = mockStatic(Character.class);
    final MockedStatic<TitlePermissionGranter> granter = mockStatic(TitlePermissionGranter.class);
    final MockedStatic<TitleSelectionMenu> menu = mockStatic(TitleSelectionMenu.class);
    final MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class);
    final Player admin = mock(Player.class);
    final Player target = mock(Player.class);
    final UUID adminId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final CharacterKey granted = new CharacterKey(targetId, "granted-on");
    final Character worn = mock(Character.class);

    CommandTitleGrantEquipTest() {
        when(admin.getUniqueId()).thenReturn(adminId);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        bukkit.when(() -> Bukkit.getPlayer(adminId)).thenReturn(admin);
        bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
        granter.when(TitlePermissionGranter::isAvailable).thenReturn(true);
        characters.when(() -> Character.get(target)).thenReturn(worn);
    }

    @AfterEach
    void close() {
        proxy.close(); menu.close(); granter.close(); characters.close(); bukkit.close();
    }

    /** Runs the private submit step (the dialog callback's body, which needs a server to reach) via reflection. */
    private void submit(String display) throws Exception {
        characters.when(() -> Character.isWearing(target, granted)).thenReturn(true);
        Method handleSubmit = CommandTitleGrant.class.getDeclaredMethod(
                "handleSubmit", Player.class, UUID.class, CharacterKey.class, String.class);
        handleSubmit.setAccessible(true);
        handleSubmit.invoke(new CommandTitleGrant(), admin, targetId, granted, display);
        proxy.verify(() -> ProxyMessagingUtil.sendByteArray(admin, "createTitle",
                adminId.toString(), targetId.toString(), "master_fisherman", "Master Fisherman"));
    }

    // Guards: a target who switched character while the proxy resolved the title got it equipped on the new character.
    @Test
    void resultAfterTargetSwitchedGrantsPermissionButDoesNotEquip() throws Exception {
        submit("Master Fisherman");
        characters.when(() -> Character.isWearing(target, granted)).thenReturn(false);

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null);

        granter.verify(() -> TitlePermissionGranter.grant(target, "master_fisherman"));
        menu.verify(() -> TitleSelectionMenu.applyTitle(any(), any(), anyString()), never());
        verify(admin, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    // Guards: the switch check must not stop the normal flow; still on the captured character, it is equipped there.
    @Test
    void resultWhileTargetStillWearsCapturedCharacterEquipsIt() throws Exception {
        submit("Master Fisherman");

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null);

        granter.verify(() -> TitlePermissionGranter.grant(target, "master_fisherman"));
        menu.verify(() -> TitleSelectionMenu.applyTitle(target, worn, "master_fisherman"));
    }

    /** Submits while the target wears {@code on}, without the fixed-display verify of {@link #submit(String)}. */
    private void submitOn(CharacterKey on, String display) throws Exception {
        characters.when(() -> Character.isWearing(target, on)).thenReturn(true);
        Method handleSubmit = CommandTitleGrant.class.getDeclaredMethod(
                "handleSubmit", Player.class, UUID.class, CharacterKey.class, String.class);
        handleSubmit.setAccessible(true);
        handleSubmit.invoke(new CommandTitleGrant(), admin, targetId, on, display);
    }

    // Guards: overlapping grants shared one pending slot, so T1's result equipped T1 on the character captured for T2.
    @Test
    void overlappingGrantsEachEquipOnlyOnTheirOwnCharacter() throws Exception {
        CharacterKey later = new CharacterKey(targetId, "switched-to");
        submitOn(granted, "Master Fisherman");
        characters.when(() -> Character.isWearing(target, granted)).thenReturn(false); // target switched
        submitOn(later, "Gold Miner");

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null);
        menu.verify(() -> TitleSelectionMenu.applyTitle(any(), any(), anyString()), never());

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "gold_miner", "Gold Miner", null);
        menu.verify(() -> TitleSelectionMenu.applyTitle(target, worn, "gold_miner"));
        menu.verify(() -> TitleSelectionMenu.applyTitle(any(), any(), eq("master_fisherman")), never());
    }

    // Guards: an identical grant submitted while the first still resolves would make the two results ambiguous.
    @Test
    void identicalGrantIsRefusedWhileTheFirstIsResolving() throws Exception {
        submitOn(granted, "Master Fisherman");
        submitOn(granted, "Master Fisherman");

        proxy.verify(() -> ProxyMessagingUtil.sendByteArray(admin, "createTitle",
                adminId.toString(), targetId.toString(), "master_fisherman", "Master Fisherman"), times(1));
        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null); // clears the pending entry for other tests
    }

    // Guards: a retry after the expiry replaced the pending character, so the first, late reply equipped onto the retry's
    // character. Two outstanding identical grants can't be told apart: neither reply may equip.
    @Test
    void retryAfterExpiryMakesBothRepliesAmbiguous() throws Exception {
        CharacterKey later = new CharacterKey(targetId, "switched-to");
        submitOn(granted, "Master Fisherman");
        expirePendingEquips();
        characters.when(() -> Character.isWearing(target, granted)).thenReturn(false); // target switched
        submitOn(later, "Master Fisherman"); // allowed after the expiry

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null); // the first, late reply
        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null); // the retry's reply

        menu.verify(() -> TitleSelectionMenu.applyTitle(any(), any(), anyString()), never());
        granter.verify(() -> TitlePermissionGranter.grant(target, "master_fisherman"), times(2));
        assertTrue(pendingEquips().isEmpty(), "both replies consumed their request");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> pendingEquips() throws Exception {
        java.lang.reflect.Field field = CommandTitleGrant.class.getDeclaredField("PENDING_EQUIPS");
        field.setAccessible(true);
        return (java.util.Map<String, Object>) field.get(null);
    }

    /** Ages every pending grant past the expiry, as if its reply never came within 60 s. */
    private static void expirePendingEquips() throws Exception {
        java.util.Map<String, Object> pending = pendingEquips();
        for (var entry : pending.entrySet()) {
            Object old = entry.getValue();
            Class<?> type = old.getClass();
            var character = type.getDeclaredMethod("character");
            var outstanding = type.getDeclaredMethod("outstanding");
            character.setAccessible(true);
            outstanding.setAccessible(true);
            var constructor = type.getDeclaredConstructor(CharacterKey.class, long.class, int.class);
            constructor.setAccessible(true);
            entry.setValue(constructor.newInstance(character.invoke(old), 0L, outstanding.invoke(old)));
        }
    }

    // Guards: a result with no pending submit (duplicate or late reply) must not equip on whatever the target wears now.
    @Test
    void resultWithoutPendingSubmitGrantsButDoesNotEquip() {
        characters.when(() -> Character.isWearing(any(), any())).thenReturn(true);

        CommandTitleGrant.handleCreateResult(adminId.toString(), targetId.toString(), true,
                "master_fisherman", "Master Fisherman", null);

        granter.verify(() -> TitlePermissionGranter.grant(target, "master_fisherman"));
        menu.verify(() -> TitleSelectionMenu.applyTitle(any(), any(), anyString()), never());
    }
}
