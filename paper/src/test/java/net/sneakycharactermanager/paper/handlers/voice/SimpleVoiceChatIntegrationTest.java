package net.sneakycharactermanager.paper.handlers.voice;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.sneakycharactermanager.paper.api.voice.TalkingNameplateLookup;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture;
import net.sneakycharactermanager.paper.handlers.nametags.NicknameEntity;

/** The real mic listener lighting real nameplates, with SVC packets and the Bukkit boundary mocked. */
class SimpleVoiceChatIntegrationTest {

    private static final byte MARKER = (byte) 0x84;
    private static final byte[] PLAIN = {0x78, 1, 2};
    private static final byte[] MARKED = {MARKER, 0x78, 1, 2};

    private NameplateFixture f;
    private final ServicesManager services = mock(ServicesManager.class);
    private TalkingBodies bodies;
    private Consumer<MicrophonePacketEvent> listener;
    private Player driver;
    private Player supporter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        f = new NameplateFixture();
        driver = f.character("Driver");
        supporter = f.character("Supporter");
        f.nick(driver, supporter);

        bodies = new TalkingBodies(services, Logger.getLogger("SimpleVoiceChatIntegrationTest"));
        EventRegistration registration = mock(EventRegistration.class);
        new SimpleVoiceChatIntegration(bodies).registerEvents(registration);
        ArgumentCaptor<Consumer<MicrophonePacketEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(registration).registerEvent(eq(MicrophonePacketEvent.class), captor.capture(), eq(101));
        listener = captor.getValue();
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private void register(TalkingNameplateLookup lookup) {
        when(services.load(TalkingNameplateLookup.class)).thenReturn(lookup);
        bodies.onServiceRegister(new ServiceRegisterEvent(new RegisteredServiceProvider<>(
                TalkingNameplateLookup.class, lookup, ServicePriority.Normal, mock(Plugin.class))));
    }

    private void registerPartnerLookup() {
        UUID driverId = driver.getUniqueId();
        UUID supporterId = supporter.getUniqueId();
        register((speaker, payload) -> speaker.equals(driverId) && payload.get(0) == MARKER ? supporterId : speaker);
    }

    private MicrophonePacketEvent packet(Player sender, byte[] payload) {
        ServerPlayer player = mock(ServerPlayer.class);
        UUID id = sender.getUniqueId();
        when(player.getUuid()).thenReturn(id);
        VoicechatConnection connection = mock(VoicechatConnection.class);
        when(connection.getPlayer()).thenReturn(player);
        MicrophonePacket packet = mock(MicrophonePacket.class);
        when(packet.getOpusEncodedData()).thenReturn(payload);
        MicrophonePacketEvent event = mock(MicrophonePacketEvent.class);
        when(event.getSenderConnection()).thenReturn(connection);
        when(event.getPacket()).thenReturn(packet);
        return event;
    }

    private NicknameEntity plate(Player player) {
        return f.plate(player).entity;
    }

    @Test
    void withoutALookupTheSenderLightsUpAndThePayloadIsNotRead() {
        MicrophonePacketEvent event = packet(driver, MARKED);
        listener.accept(event);
        verify(plate(driver)).setTalking(true);
        verify(plate(supporter), never()).setTalking(anyBoolean());
        verify(event, never()).getPacket();

        f.ticks(10);
        verify(plate(driver)).setTalking(false);
    }

    @Test
    void partnerSpeechLightsUpTheBodyAndNotTheSpeaker() {
        registerPartnerLookup();
        listener.accept(packet(driver, MARKED));
        listener.accept(packet(driver, new byte[0])); // End of talk.
        verify(plate(supporter)).setTalking(true);
        verify(plate(driver), never()).setTalking(anyBoolean());

        f.ticks(10);
        verify(plate(supporter)).setTalking(false);
        verify(plate(driver), never()).setTalking(anyBoolean());
    }

    @Test
    void theSpeakersOwnVoiceStillLightsTheSpeaker() {
        registerPartnerLookup();
        listener.accept(packet(driver, PLAIN));
        verify(plate(driver)).setTalking(true);
        verify(plate(supporter), never()).setTalking(anyBoolean());
    }

    @Test
    void bothVoicesInOneBodyKeepItLitUntilBothStop() {
        registerPartnerLookup();
        listener.accept(packet(driver, MARKED));
        f.ticks(5);
        listener.accept(packet(supporter, PLAIN));
        f.ticks(5);
        verify(plate(supporter)).setTalking(true);
        verify(plate(supporter), never()).setTalking(false);

        f.ticks(5);
        verify(plate(supporter)).setTalking(false);
    }

    @Test
    void anOfflineBodyLightsNothing() {
        registerPartnerLookup();
        f.quit(supporter);
        listener.accept(packet(driver, MARKED));
        verify(plate(supporter), never()).setTalking(anyBoolean());
        verify(plate(driver), never()).setTalking(anyBoolean());
    }
}
