package net.sneakycharactermanager.velocity;

import net.sneakycharactermanager.proxy.common.ProxyLogger;

import org.slf4j.Logger;

public final class VelocityLogger implements ProxyLogger {
    private final Logger logger;

    public VelocityLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void severe(String message, Throwable t) {
        logger.error(message, t);
    }
}

