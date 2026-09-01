package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PendingCharacterJoinTaskTest {

    @Test
    void offlinePlayerDoesNotRequireTaskMapEntryToCancel() {
        Player offlinePlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("isOnline") ? false : defaultValue(method.getReturnType()));

        AtomicBoolean cancelled = new AtomicBoolean();
        PendingCharacterJoinTask task = new PendingCharacterJoinTask(
                offlinePlayer,
                () -> cancelled.set(true),
                () -> false,
                () -> false,
                () -> {},
                () -> {});

        assertDoesNotThrow(task::run);
        org.junit.jupiter.api.Assertions.assertTrue(cancelled.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
