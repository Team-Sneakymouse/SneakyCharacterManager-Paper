package net.sneakycharactermanager.bungee;

import net.sneakycharactermanager.proxy.common.ProxyLogger;

import java.util.logging.Logger;

public final class BungeeLogger implements ProxyLogger {
    private final Logger logger;

    public BungeeLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override
    public void severe(String message) {
        logger.severe(message);
    }

    @Override
    public void severe(String message, Throwable t) {
        logger.severe(message);
        t.printStackTrace();
    }
}

