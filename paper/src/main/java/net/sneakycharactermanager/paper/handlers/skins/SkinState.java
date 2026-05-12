package net.sneakycharactermanager.paper.handlers.skins;

import javax.annotation.Nullable;

public final class SkinState {

    private final int id;
    private final String name;
    private final String texture;
    private final String signature;
    private final String characterUUID;
    private final String proxyTextureUrl;
    private final String proxyTexture;
    private final String proxySignature;
    @Nullable private final Integer previousId;

    public SkinState(
            int id,
            String name,
            String texture,
            String signature,
            String characterUUID,
            String proxyTextureUrl,
            String proxyTexture,
            String proxySignature,
            @Nullable Integer previousId
    ) {
        this.id = id;
        this.name = name;
        this.texture = texture;
        this.signature = signature;
        this.characterUUID = characterUUID;
        this.proxyTextureUrl = proxyTextureUrl;
        this.proxyTexture = proxyTexture;
        this.proxySignature = proxySignature;
        this.previousId = previousId;
    }

    public int id() { return id; }
    public String name() { return name; }
    public String texture() { return texture; }
    public String signature() { return signature; }
    public String characterUUID() { return characterUUID; }
    public String proxyTextureUrl() { return proxyTextureUrl; }
    public String proxyTexture() { return proxyTexture; }
    public String proxySignature() { return proxySignature; }
    @Nullable public Integer previousId() { return previousId; }
}
