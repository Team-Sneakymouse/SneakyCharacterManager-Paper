package net.sneakycharactermanager.bungee;

import java.io.*;
import java.security.*;

import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;

public class SneakyCharacterManager extends Plugin {

	public static final String IDENTIFIER = "sneakycharacters";

	private static SneakyCharacterManager instance;

	private PrivateKey privateKey;
	private PublicKey publicKey;

	@Override
	public void onEnable() {
		instance = this;

		loadOrGenerateKeys();

		getProxy().registerChannel("sneakymouse:" + IDENTIFIER);
		getProxy().getPluginManager().registerListener(this, new PluginMessageListener());
		getProxy().getPluginManager().registerListener(this, new ConnectionEventListeners());
	}

	private void loadOrGenerateKeys() {
		try {
			File keyFile = new File(getDataFolder(), "keys.ser");
			if (keyFile.exists()) {
				readKeysFromFile(keyFile);
				getLogger().info("Loaded existing keys from " + keyFile.getAbsolutePath());
			} else {
				generateNewKeyPair();
				saveKeysToFile(keyFile);
				getLogger().info("Generated new keys and saved to " + keyFile.getAbsolutePath());
			}
		} catch (Exception e) {
			getLogger().severe("Error loading or generating keys: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void readKeysFromFile(File keyFile) throws Exception {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
			this.privateKey = (PrivateKey) ois.readObject();
			this.publicKey = (PublicKey) ois.readObject();
		}
	}

	private void generateNewKeyPair() throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		this.privateKey = keyPair.getPrivate();
		this.publicKey = keyPair.getPublic();
	}

	private void saveKeysToFile(File keyFile) throws Exception {
		keyFile.getParentFile().mkdirs();
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(keyFile))) {
			oos.writeObject(this.privateKey);
			oos.writeObject(this.publicKey);
		}
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public static SneakyCharacterManager getInstance() {
		return instance;
	}

	public static File getCharacterDataFolder() {
		File dir = new File(getInstance().getDataFolder(), "characterdata");
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}
}