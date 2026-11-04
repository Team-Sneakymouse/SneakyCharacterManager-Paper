package net.sneakycharactermanager.paper.handlers.titles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

/** The LuckPerms subscription, driven through LuckPerms' API boundary (no LuckPerms implementation). */
class TitlePermissionRefreshTest {

    @Test
    @SuppressWarnings("unchecked")
    void recalculationRefreshesThatUsersTitleAndTheSubscriptionIsReturnedForClosing() throws Exception {
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        plugin.nametagManager = mock(NametagManager.class);
        LuckPerms luckPerms = mock(LuckPerms.class);
        EventBus bus = mock(EventBus.class);
        EventSubscription<UserDataRecalculateEvent> subscription = mock(EventSubscription.class);
        when(luckPerms.getEventBus()).thenReturn(bus);
        when(bus.subscribe(eq(plugin), eq(UserDataRecalculateEvent.class), any(Consumer.class))).thenReturn(subscription);

        EventSubscription<UserDataRecalculateEvent> registered;
        try (MockedStatic<LuckPermsProvider> provider = mockStatic(LuckPermsProvider.class)) {
            provider.when(LuckPermsProvider::get).thenReturn(luckPerms);
            registered = TitlePermissionRefresh.register(plugin);
        }
        assertSame(subscription, registered);

        ArgumentCaptor<Consumer<? super UserDataRecalculateEvent>> handler = ArgumentCaptor.forClass(Consumer.class);
        verify(bus).subscribe(eq(plugin), eq(UserDataRecalculateEvent.class), handler.capture());

        UUID id = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getUniqueId()).thenReturn(id);
        UserDataRecalculateEvent event = mock(UserDataRecalculateEvent.class);
        when(event.getUser()).thenReturn(user);
        handler.getValue().accept(event);
        verify(plugin.nametagManager).refreshTitleSoon(id);

        // SneakyCharacterManager holds it as AutoCloseable and closes it on disable.
        ((AutoCloseable) registered).close();
        verify(subscription).close();
    }
}
