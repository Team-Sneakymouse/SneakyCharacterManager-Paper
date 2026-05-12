package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;

import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class ProxyKeys {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private ProxyKeys(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public PrivateKey privateKey() { return privateKey; }
    public PublicKey publicKey() { return publicKey; }

    public static ProxyKeys loadOrGenerate(File dataFolder, ProxyLogger logger) {
        try {
            File keyFile = new File(dataFolder, "keys.ser");
            if (keyFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
                    PrivateKey priv = (PrivateKey) ois.readObject();
                    PublicKey pub = (PublicKey) ois.readObject();
                    logger.info("Loaded existing keys from " + keyFile.getAbsolutePath());
                    return new ProxyKeys(priv, pub);
                }
            }

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            keyFile.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(keyFile))) {
                oos.writeObject(keyPair.getPrivate());
                oos.writeObject(keyPair.getPublic());
            }

            logger.info("Generated new keys and saved to " + keyFile.getAbsolutePath());
            return new ProxyKeys(keyPair.getPrivate(), keyPair.getPublic());
        } catch (Exception e) {
            logger.severe("Error loading or generating keys: " + e.getMessage(), e);
            return new ProxyKeys(null, null);
        }
    }
}

