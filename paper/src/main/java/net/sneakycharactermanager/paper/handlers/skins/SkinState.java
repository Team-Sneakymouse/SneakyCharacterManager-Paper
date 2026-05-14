package net.sneakycharactermanager.paper.handlers.skins;

import javax.annotation.Nullable;

public final class SkinState {

    /** Reserved id for {@link #pendingCharacterSkinChange} snapshots not yet stored in {@link SkinStateManager}. */
    public static final int PENDING_SKIN_CHANGE_ID = 0;

    private final int id;
    private final String name;
    private final String texture;
    private final String signature;
    private final String characterUUID;
    private final String proxyTextureUrl;
    private final String proxyTexture;
    private final String proxySignature;
    @Nullable private final Integer previousId;
    /** Only set on {@link #pendingCharacterSkinChange}; {@code null} for applied states. */
    @Nullable private final Boolean slimModel;

    public SkinState(
            int id,
            String name,
            String texture,
            String signature,
            String characterUUID,
            String proxyTextureUrl,
            String proxyTexture,
            String proxySignature,
            @Nullable Integer previousId,
            @Nullable Boolean slimModel
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
        this.slimModel = slimModel;
    }

    /**
     * Snapshot for a skin change that has not been applied yet (e.g. {@link net.sneakycharactermanager.paper.handlers.character.CharacterSkinChangeEvent}).
     * {@link #proxyTextureUrl()} holds the requested HTTP skin URL, or the literal {@code "default"} for Mojang default revert.
     * Texture/signature are empty until MineSkin (or revert) completes.
     */
    public static SkinState pendingCharacterSkinChange(String characterUUID, String skinUrlOrDefault, @Nullable Boolean slim) {
        return new SkinState(PENDING_SKIN_CHANGE_ID, "Regular", "", "", characterUUID, skinUrlOrDefault, "", "", null, slim);
    }

    public boolean isPendingSkinChange() {
        return id == PENDING_SKIN_CHANGE_ID;
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
    @Nullable public Boolean slimModel() { return slimModel; }
}
