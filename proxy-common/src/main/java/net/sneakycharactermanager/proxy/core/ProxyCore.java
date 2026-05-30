package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.SkinContentHash;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProxyCore {

    private static final int SKIN_RESOLVE_TIMEOUT_SECONDS = 5;
    private static final int SKIN_DOWNLOAD_MAX_BYTES = 1024 * 1024;

    private final ProxyPlatform platform;
    private final PlayerDataRepository playerDataRepository;
    private final UniformSkinCache uniformSkinCache;
    private final GlobalSkinCache globalSkinCache;
    private final SkinResolveService skinResolveService;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final ProxyMessenger messenger;

    private final Set<String> handledRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ProxyCore(ProxyPlatform platform, ProxyKeys keys) {
        this.platform = platform;
        this.privateKey = keys.privateKey();
        this.publicKey = keys.publicKey();

        this.globalSkinCache = new GlobalSkinCache(platform.dataFolder(), platform.logger());
        this.uniformSkinCache = new UniformSkinCache(platform.dataFolder(), platform.logger());
        this.playerDataRepository = new PlayerDataRepository(platform, globalSkinCache);
        this.skinResolveService = new SkinResolveService(globalSkinCache, SKIN_RESOLVE_TIMEOUT_SECONDS, SKIN_DOWNLOAD_MAX_BYTES, platform.logger());
        this.messenger = new ProxyMessenger(platform, privateKey, uniformSkinCache, globalSkinCache);
    }

    public void onPlayerDisconnect(UUID playerUniqueId) {
        playerDataRepository.remove(playerUniqueId.toString());
    }

    public void onPluginMessage(ProxyServerConnection server, byte[] rawData) {
        if (publicKey == null) return;

        ProxyMessageIO.IncomingVerified decoded =
                ProxyMessageIO.decodeAndVerifyIncoming(rawData, publicKey, platform.logger());
        if (decoded == null) return;

        if (!handledRequests.add(decoded.requestUuid())) {
            platform.logger().warning("Received duplicated message! Ignoring");
            return;
        }

        try {
            handleVerified(server, decoded.subChannel(), decoded.payload());
        } catch (Exception e) {
            platform.logger().severe("Error handling subchannel " + decoded.subChannel(), e);
        }
    }

    private void handleVerified(ProxyServerConnection server, String subChannel, DataInputStream in) throws IOException {
        switch (subChannel) {
            case "playerJoin" -> {
                String playerUUID = in.readUTF();
                PlayerData pd = playerDataRepository.get(playerUUID);
                pd.loadLastPlayedCharacter(server, messenger);
            }
            case "playerQuit" -> {
                String playerUUID = in.readUTF();
                playerDataRepository.remove(playerUUID);
            }
            case "characterSelectionGUI" -> {
                String playerUUID = in.readUTF();
                String requesterUUID = in.readUTF();
                playerDataRepository.get(playerUUID).sendEnabledCharacters(server, messenger, subChannel, requesterUUID);
            }
            case "preloadSkins" -> {
                String playerUUID = in.readUTF();
                playerDataRepository.get(playerUUID).sendEnabledCharacters(server, messenger, subChannel, playerUUID);
            }
            case "selectCharacter" -> {
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                playerDataRepository.get(playerUUID).loadCharacter(server, messenger, characterUUID, false);
            }
            case "tempCharacter" -> {
                String requesterUUID = in.readUTF();
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                playerDataRepository.get(playerUUID).loadTempCharacter(server, messenger, requesterUUID, characterUUID);
            }
            case "selectCharacterByName" -> {
                String playerUUID = in.readUTF();
                String characterName = in.readUTF();
                playerDataRepository.get(playerUUID).loadCharacterByName(server, messenger, characterName);
            }
            case "updateCharacter" -> {
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                int type = in.readInt();

                PlayerData pd = playerDataRepository.get(playerUUID);
                switch (type) {
                    case 1 -> pd.setCharacterSkin(characterUUID, in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readBoolean());
                    case 6 -> pd.setCharacterSkinById(characterUUID, in.readUTF(), in.readBoolean());
                    case 2 -> {
                        pd.setCharacterName(characterUUID, in.readUTF());
                        pd.updateCharacterList(server, messenger);
                    }
                    case 3 -> {
                        boolean newEnabled = in.readBoolean();
                        CharacterData character = pd.getCharacter(characterUUID);
                        pd.setCharacterEnabled(characterUUID, newEnabled);
                        if (!newEnabled && character != null) {
                            messenger.send(server, "deleteConfirmed", playerUUID, character.name(), characterUUID);
                        }
                        pd.updateCharacterList(server, messenger);
                    }
                    case 4 -> pd.setCharacterTags(characterUUID, in.readUTF());
                    case 5 -> {
                        pd.setCharacterGender(characterUUID, in.readUTF());
                        pd.updateCharacterList(server, messenger);
                    }
                    default -> { /* ignore */ }
                }
            }
            case "defaultSkin" -> {
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                String url = in.readUTF();
                boolean slim = in.readBoolean();
                playerDataRepository.get(playerUUID).setCharacterSkin(characterUUID, url, "", "", "", slim);
            }
            case "createNewCharacter" -> {
                String playerUUID = in.readUTF();
                UUID u = UUID.fromString(playerUUID);
                String name = platform.playerName(u);
                if (name == null) name = "";

                PlayerData pd = playerDataRepository.get(playerUUID);
                String characterUUID = pd.createNewCharacter(name);
                if (characterUUID != null) pd.loadCharacter(server, messenger, characterUUID, true);
            }
            case "saveTemplateChar" -> {
                String characterID = in.readUTF();
                String characterName = in.readUTF();
                String characterSkin = in.readUTF();
                String skinUUID = in.readUTF();
                boolean characterSlim = in.readBoolean();

                PlayerData pd = playerDataRepository.get("template");
                pd.createNewCharacter(characterID, characterName, characterSkin, skinUUID, characterSlim);
            }
            case "saveUniformVariant" -> {
                String baseUrl = in.readUTF();
                String uHash = in.readUTF();
                String sUUID = in.readUTF();
                String tUrl = in.readUTF();
                String tex = in.readUTF();
                String sig = in.readUTF();
                uniformSkinCache.addVariant(baseUrl, uHash, sUUID, tUrl, tex, sig);
            }
            case "resolveSkin" -> {
                String playerUUID = in.readUTF();
                String requestId = in.readUTF();
                String sourceUrl = in.readUTF();
                @SuppressWarnings("unused")
                boolean slim = in.readBoolean();
                if (SkinContentHash.isMojangTextureUrl(sourceUrl)) {
                    sourceUrl = SkinContentHash.normalizeMojangTextureUrl(sourceUrl);
                }
                final String urlFinal = sourceUrl;
                skinResolveService.resolve(urlFinal).whenComplete((result, err) -> {
                    SkinResolveService.Result toSend = result;
                    if (err != null || toSend == null) {
                        platform.logger().warning("resolveSkin failed for " + urlFinal + ": "
                                + (err != null ? err.getMessage() : "null result"));
                        toSend = SkinResolveService.Result.error(err != null ? err.getMessage() : "Resolve failed");
                    } else if (toSend.status == SkinResolveService.Status.HIT) {
                        platform.logger().info("resolveSkin HIT " + urlFinal + " -> " + toSend.skinId);
                    } else {
                        platform.logger().info("resolveSkin " + toSend.status + " for " + urlFinal
                                + (toSend.skinId.isEmpty() ? "" : " (id " + toSend.skinId + ")"));
                    }
                    messenger.send(server, "resolveSkinResult", playerUUID, requestId,
                            toSend.status.name(),
                            toSend.skinId,
                            toSend.mojangTextureUrl,
                            toSend.texture,
                            toSend.signature,
                            toSend.errorMessage);
                });
            }
            case "registerSkin" -> {
                String skinId = in.readUTF();
                String mojangUrl = in.readUTF();
                String texture = in.readUTF();
                String signature = in.readUTF();
                String sourceUrl = in.readUTF();
                GlobalSkinCache.Entry entry = new GlobalSkinCache.Entry(skinId, mojangUrl, texture, signature);
                globalSkinCache.putAndIndexSourceUrl(entry, sourceUrl);
            }
            case "getAllCharacters" -> {
                String requesterUUID = in.readUTF();
                String filter = in.readUTF();
                try {
                    List<String> data = playerDataRepository.getAllCharacters(filter);
                    messenger.send(server, "getAllCharacters", requesterUUID, data);
                } catch (IOException ignored) {
                    // best-effort
                }
            }
            default -> platform.logger().severe("Unknown proxy subchannel: " + subChannel);
        }
    }
}
