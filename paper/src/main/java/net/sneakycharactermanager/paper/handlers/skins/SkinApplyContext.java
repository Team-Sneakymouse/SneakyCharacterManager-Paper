package net.sneakycharactermanager.paper.handlers.skins;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

public final class SkinApplyContext {

    @Nullable private final String skinStateLabel;
    @Nullable private final SkullMeta skullMeta;
    @Nullable private final ItemStack characterHead;
    @Nullable private final Inventory inventory;
    private final int index;
    private final boolean recordSkinState;
    private final boolean updateProxyCharacter;

    private SkinApplyContext(@Nullable String skinStateLabel, @Nullable SkullMeta skullMeta,
                             @Nullable ItemStack characterHead, @Nullable Inventory inventory, int index,
                             boolean recordSkinState, boolean updateProxyCharacter) {
        this.skinStateLabel = skinStateLabel;
        this.skullMeta = skullMeta;
        this.characterHead = characterHead;
        this.inventory = inventory;
        this.index = index;
        this.recordSkinState = recordSkinState;
        this.updateProxyCharacter = updateProxyCharacter;
    }

    public static SkinApplyContext defaults() {
        return new SkinApplyContext(null, null, null, null, 0, true, true);
    }

    public static SkinApplyContext forMenu(SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        return new SkinApplyContext(null, skullMeta, characterHead, inventory, index, false, false);
    }

    public static SkinApplyContext withSkinStateLabel(String label) {
        return new SkinApplyContext(label, null, null, null, 0, true, true);
    }

    /** Preload / queue only — does not apply to the player or update proxy character YAML. */
    public static SkinApplyContext preload() {
        return new SkinApplyContext(null, null, null, null, 0, false, false);
    }

    @Nullable public String skinStateLabel() { return skinStateLabel; }
    @Nullable public SkullMeta skullMeta() { return skullMeta; }
    @Nullable public ItemStack characterHead() { return characterHead; }
    @Nullable public Inventory inventory() { return inventory; }
    public int index() { return index; }
    public boolean recordSkinState() { return recordSkinState; }
    public boolean updateProxyCharacter() { return updateProxyCharacter; }
}
