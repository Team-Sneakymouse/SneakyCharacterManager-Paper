package net.sneakycharactermanager.paper.handlers.voice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import net.sneakycharactermanager.paper.api.voice.TalkingNameplateLookup;

class TalkingBodiesTest {

    private static final byte MARKER = (byte) 0x84;

    private final ServicesManager services = mock(ServicesManager.class);
    private final Plugin dancing = mock(Plugin.class);
    private final Logger logger = Logger.getLogger("TalkingBodiesTest");
    private final List<LogRecord> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { logged.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    };
    private final UUID driver = UUID.randomUUID();
    private final UUID supporter = UUID.randomUUID();
    private TalkingBodies bodies;

    @BeforeEach
    void setUp() {
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
        bodies = new TalkingBodies(services, logger);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(capture);
    }

    /** Registers {@code lookup} the way Bukkit does: the service changes, then the event fires. */
    private RegisteredServiceProvider<TalkingNameplateLookup> register(TalkingNameplateLookup lookup) {
        var provider = new RegisteredServiceProvider<>(TalkingNameplateLookup.class, lookup, ServicePriority.Normal, dancing);
        when(services.load(TalkingNameplateLookup.class)).thenReturn(lookup);
        bodies.onServiceRegister(registered(provider));
        return provider;
    }

    // Service events read Bukkit.isPrimaryThread() when constructed, so the tests mock them.
    private static ServiceRegisterEvent registered(RegisteredServiceProvider<?> provider) {
        ServiceRegisterEvent event = mock(ServiceRegisterEvent.class);
        doReturn(provider).when(event).getProvider();
        return event;
    }

    private static ServiceUnregisterEvent unregistered(RegisteredServiceProvider<?> provider) {
        ServiceUnregisterEvent event = mock(ServiceUnregisterEvent.class);
        doReturn(provider).when(event).getProvider();
        return event;
    }

    /** Maps the Driver's packets that start with the marker to the Supporter, as Shadow Dancing will. */
    private TalkingNameplateLookup partnerLookup() {
        return (speaker, payload) -> speaker.equals(driver) && payload.get(0) == MARKER ? supporter : speaker;
    }

    @Test
    void withoutALookupEveryPacketIsItsSenders() {
        assertFalse(bodies.active());
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {MARKER, 1, 2}));
        assertEquals(driver, bodies.bodyFor(driver, new byte[0]));
    }

    @Test
    void aRegisteredLookupMapsTheMarkedPacketToTheBody() {
        register(partnerLookup());
        assertTrue(bodies.active());
        assertEquals(supporter, bodies.bodyFor(driver, new byte[] {MARKER, 1, 2}));
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {0x78, 1, 2}));
        assertEquals(supporter, bodies.bodyFor(supporter, new byte[] {0x78, 1, 2}));
    }

    @Test
    void theLookupSeesTheWholePayloadReadOnly() {
        byte[] payload = {MARKER, 1, 2};
        List<ByteBuffer> seen = new ArrayList<>();
        register((speaker, buffer) -> {
            seen.add(buffer);
            assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0));
            return speaker;
        });
        bodies.bodyFor(driver, payload);

        ByteBuffer buffer = seen.getFirst();
        assertEquals(0, buffer.position());
        assertEquals(3, buffer.limit());
        assertEquals(MARKER, buffer.get(0));
        assertArrayEquals(new byte[] {MARKER, 1, 2}, payload, "the packet's own bytes are untouched");
    }

    @Test
    void endOfTalkLightsNothingAndIsNotAskedAbout() {
        TalkingNameplateLookup lookup = mock(TalkingNameplateLookup.class);
        register(lookup);
        assertNull(bodies.bodyFor(driver, new byte[0]));
        verifyNoInteractions(lookup);
    }

    @Test
    void aNullAnswerMeansTheSender() {
        register((speaker, payload) -> null);
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {1}));
    }

    @Test
    void aFailingLookupFallsBackToTheSenderAndLogsOnce() {
        register((speaker, payload) -> { throw new IllegalStateException("broken"); });
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {1}));
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {1}));
        assertEquals(1, logged.size());
        assertInstanceOf(IllegalStateException.class, logged.getFirst().getThrown());
    }

    @Test
    void unregisteringRestoresTheSender() {
        var provider = register(partnerLookup());
        when(services.load(TalkingNameplateLookup.class)).thenReturn(null);
        bodies.onServiceUnregister(unregistered(provider));
        assertFalse(bodies.active());
        assertEquals(driver, bodies.bodyFor(driver, new byte[] {MARKER, 1}));
    }

    @Test
    void otherServicesDoNotReloadTheLookup() {
        clearInvocations(services);
        var other = new RegisteredServiceProvider<>(Runnable.class, () -> {}, ServicePriority.Normal, dancing);
        bodies.onServiceRegister(registered(other));
        bodies.onServiceUnregister(unregistered(other));
        verifyNoInteractions(services);
    }

    @Test
    void startListensBeforeLoadingALookupRegisteredEarlier() {
        TalkingNameplateLookup lookup = partnerLookup();
        when(services.load(TalkingNameplateLookup.class)).thenReturn(lookup);
        PluginManager plugins = mock(PluginManager.class);
        Plugin scm = mock(Plugin.class);
        TalkingBodies late = new TalkingBodies(services, logger);
        late.start(plugins, scm);

        InOrder order = inOrder(plugins, services);
        order.verify(plugins).registerEvents(late, scm);
        order.verify(services).load(TalkingNameplateLookup.class);
        assertEquals(supporter, late.bodyFor(driver, new byte[] {MARKER}));
    }
}
