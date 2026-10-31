package net.sneakycharactermanager.paper.storage;

import java.util.*;
import net.sneakycharactermanager.proxy.common.SkinContentHash;

/** Immutable identity data; conversion to Bukkit Character happens only on the main thread. */
public record CharacterView(String id, boolean enabled, String name, String skinId, String skin,
        String skinUuid, String texture, String signature, boolean slim, String tags, String gender, String title, String uniformBase) {
    public static CharacterView from(Map<String,Object> row) {
        String id = SqlStore.text(row, "skin_id");
        boolean cached = !id.isEmpty() && !SqlStore.text(row, "cached_id").isEmpty();
        boolean inline = id.isEmpty() || (!SqlStore.text(row, "texture").isEmpty() && !SqlStore.text(row, "signature").isEmpty());
        String texture = SqlStore.text(row, cached ? "cached_texture" : "texture");
        String signature = SqlStore.text(row, cached ? "cached_signature" : "signature");
        String url = cached ? SqlStore.text(row, "cached_url") : inline ? SqlStore.text(row, "skin") : "";
        String fromTexture = SkinContentHash.mojangUrlFromTextureProperty(texture);
        if (!cached && inline && fromTexture != null && !fromTexture.isEmpty()) url = fromTexture;
        if (!cached && !inline) { texture = ""; signature = ""; }
        String legacyBase = SkinContentHash.mojangUrlFromTextureProperty(SqlStore.text(row, "texture"));
        String uniformBase = legacyBase != null && !legacyBase.isEmpty() ? legacyBase : !url.isEmpty() ? url : SqlStore.text(row, "skin");
        return new CharacterView(SqlStore.text(row, "character_id"), Boolean.TRUE.equals(row.get("enabled")),
                SqlStore.text(row, "name"), id, url, cached ? "" : SqlStore.text(row, "skin_uuid"),
                texture, signature, Boolean.TRUE.equals(row.get("slim")), SqlStore.text(row, "tags"),
                SqlStore.text(row, "gender"), SqlStore.text(row, "title"), uniformBase);
    }
    public String plainName() { return name.replaceAll("<[^>]*>|&[0-9A-FK-ORa-fk-or]", ""); }
}
