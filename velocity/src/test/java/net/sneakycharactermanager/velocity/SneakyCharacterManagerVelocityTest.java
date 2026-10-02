package net.sneakycharactermanager.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SneakyCharacterManagerVelocityTest {

    @Test
    void pluginMessagesAreFilteredBeforeAsyncDispatch() throws Exception {
        Method handler = SneakyCharacterManagerVelocity.class.getMethod(
                "onPluginMessage",
                PluginMessageEvent.class
        );
        Subscribe subscription = handler.getAnnotation(Subscribe.class);

        assertFalse(subscription.async(), "Velocity must invoke the channel filter synchronously");
        assertEquals(EventTask.class, handler.getReturnType(),
                "matching SCM messages should return asynchronous work");
    }
}
