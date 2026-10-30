package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import java.io.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyCompatibilityTest {
    @Test void defaultModeStillSendsOriginalSignedVelocityAndBungeeWirePayload() throws Exception {
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        Player player = mock(Player.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        when(plugin.getStorageMode()).thenReturn(StorageMode.parse(null));
        when(plugin.getPrivateKey()).thenReturn(keys.getPrivate());
        try (var singleton = mockStatic(SneakyCharacterManager.class)) {
            singleton.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
            ProxyMessagingUtil.sendByteArray(player, "playerJoin", "owner-id");
        }
        var bytes = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq("sneakymouse:sneakycharacters"), bytes.capture());
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.getValue()));
        String signed = in.readUTF(); byte[] payload = in.readAllBytes();
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initVerify(keys.getPublic()); signature.update(payload);
        assertTrue(signature.verify(Base64.getDecoder().decode(signed)));
        DataInputStream decoded = new DataInputStream(new ByteArrayInputStream(payload));
        assertTrue(decoded.readUTF().startsWith("playerJoin_UUID:"));
        assertEquals("owner-id", decoded.readUTF()); assertEquals(0, decoded.available());
    }
    @Test void databaseModeDoesNotFallThroughToLegacyTransport() {
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        Player player = mock(Player.class);
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        try (var singleton = mockStatic(SneakyCharacterManager.class)) {
            singleton.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
            ProxyMessagingUtil.sendByteArray(player, "playerJoin", "owner-id");
        }
        verifyNoInteractions(player); verify(plugin, never()).getPrivateKey();
    }
    @Test void paperEntrypointAndLegacyTransportLinkWithoutAnySneakyDbClasses() throws Exception {
        var urls = new java.net.URL[] { SneakyCharacterManager.class.getProtectionDomain().getCodeSource().getLocation() };
        try (var loader = new java.net.URLClassLoader(urls, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.sneakydb.")) throw new ClassNotFoundException("SneakyDB deliberately absent");
                if (name.startsWith("net.sneakycharactermanager.paper.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> c = findLoadedClass(name);
                        if (c == null) c = findClass(name);
                        if (resolve) resolveClass(c);
                        return c;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertNotNull(Class.forName(SneakyCharacterManager.class.getName(), false, loader).getDeclaredMethods());
            assertNotNull(Class.forName(ProxyMessagingUtil.class.getName(), false, loader).getDeclaredMethods());
            Class<?> mode = Class.forName(StorageMode.class.getName(), true, loader);
            assertEquals("LEGACY", mode.getMethod("parse", String.class).invoke(null, new Object[]{null}).toString());
        }
    }
}
