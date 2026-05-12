package net.sneakycharactermanager.proxy.common;

/**
 * Represents the backend server connection that sent a plugin message to the proxy.
 * The proxy replies back to this connection using plugin messaging.
 */
public interface ProxyServerConnection {
    void sendPluginMessage(byte[] data);
    String debugName();
}

