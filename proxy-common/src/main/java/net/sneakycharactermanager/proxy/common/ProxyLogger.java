package net.sneakycharactermanager.proxy.common;

public interface ProxyLogger {
    void info(String message);
    void warning(String message);
    void severe(String message);
    void severe(String message, Throwable t);
}

