package net.sneakycharactermanager.paper.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import net.sneakycharactermanager.paper.handlers.character.CharacterSkinAppliedEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinAppliedEventTest {
    @Test void cancellationLeavesPlayerAndProfileCompletelyUntouched() {
        Player player = mock(Player.class);
        PluginManager manager = mock(PluginManager.class);
        ProfileProperty property = new ProfileProperty("textures", "value", "signature");
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            assertInstanceOf(org.bukkit.event.Cancellable.class, event);
            var cancellable = (org.bukkit.event.Cancellable) event;
            assertFalse(cancellable.isCancelled());
            cancellable.setCancelled(true);
            assertTrue(cancellable.isCancelled());
            return null;
        }).when(manager).callEvent(any());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            SkinUtil.applySkin(player, property);
        }
        verify(manager).callEvent(any());
        verifyNoInteractions(player);
    }

    @Test void dispatchesResolvedSignedPropertySynchronouslyBeforePreparation() throws Exception {
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        Entity vehicle = mock(Entity.class);
        PluginManager manager = mock(PluginManager.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        when(player.getVehicle()).thenReturn(vehicle);
        when(player.getLocation()).thenReturn(new Location(null, 1, 2, 3));
        String url = "http://textures.minecraft.net/texture/resolved";
        String value = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\",\"metadata\":{\"model\":\"slim\"}}}}").getBytes(StandardCharsets.UTF_8));
        ProfileProperty property = new ProfileProperty("textures", value, "resolved-signature");
        AtomicReference<Event> dispatched = new AtomicReference<>();
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            dispatched.set(event);
            assertEquals("CharacterSkinAppliedEvent", event.getEventName());
            assertFalse(event.isAsynchronous());
            CharacterSkinAppliedEvent applied = assertInstanceOf(CharacterSkinAppliedEvent.class, event);
            assertSame(player, applied.getPlayer());
            assertSame(property, applied.getProfileProperty());
            assertEquals(value, applied.getProfileProperty().getValue());
            assertEquals("resolved-signature", applied.getProfileProperty().getSignature());
            assertEquals(url, applied.getSkinUrl());
            assertSame(CharacterSkinAppliedEvent.getHandlerList(), applied.getHandlers());
            verify(player, never()).getPlayerProfile();
            verifyNoInteractions(profile, vehicle);
            return null;
        }).when(manager).callEvent(any());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            SkinUtil.applySkin(player, property);
        }
        assertNotNull(dispatched.get(), "applySkin must dispatch the resolved event");
        verify(manager, times(1)).callEvent(any());
        var order = inOrder(profile, player, vehicle);
        order.verify(profile).removeProperty("textures");
        order.verify(profile).setProperty(property);
        order.verify(player).setPlayerProfile(profile);
        order.verify(vehicle).removePassenger(player);
        order.verify(player).teleport(new Location(null, 1, 3, 3));
    }

    @Test void askingFiresTheEventAndReportsACancellationWithoutTouchingThePlayer() {
        Player player = mock(Player.class);
        PluginManager manager = mock(PluginManager.class);
        ProfileProperty property = new ProfileProperty("textures", "value", "signature");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertTrue(SkinUtil.skinAllowed(player, property));
            doAnswer(call -> { ((CharacterSkinAppliedEvent) call.getArgument(0)).setCancelled(true); return null; }).when(manager).callEvent(any());
            assertFalse(SkinUtil.skinAllowed(player, property));
        }
        verify(manager, times(2)).callEvent(any(CharacterSkinAppliedEvent.class));
        verifyNoInteractions(player);
    }

    @Test void puttingTheSkinOnSetsTheProfileWithoutAnEventOrMovingThePlayer() {
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        PluginManager manager = mock(PluginManager.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        ProfileProperty property = new ProfileProperty("textures", "value", "signature");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            SkinUtil.putSkin(player, property);
        }
        verifyNoInteractions(manager);
        verify(profile).setProperty(property);
        verify(player).setPlayerProfile(profile);
        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).getVehicle();
    }

    @Test void puttingNoSkinOnRemovesTheTexturesProperty() {
        Player player = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(player.getPlayerProfile()).thenReturn(profile);

        SkinUtil.putSkin(player, null);

        var order = inOrder(profile, player);
        order.verify(profile).removeProperty("textures");
        order.verify(player).setPlayerProfile(profile);
        verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
