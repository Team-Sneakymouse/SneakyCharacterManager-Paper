package net.sneakycharactermanager.paper.handlers.skins;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.sneakycharactermanager.paper.util.ChatUtility;

import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinStateManager {

    /**
     * Turns a uniform map key (e.g. file stem) into a short display label:
     * dashes and underscores become spaces, lowercased, then first character uppercased.
     * Pure 64-char hex keys (uniform hash cache keys) fall back to {@code "Uniform"}.
     */
    public static String uniformKeyToDisplayName(@Nullable String uniformKey) {
        if (uniformKey == null || uniformKey.isBlank()) {
            return "Uniform";
        }
        String trimmed = uniformKey.trim();
        if (trimmed.length() == 64 && trimmed.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            return "Uniform";
        }
        String spaced = trimmed.replace('-', ' ').replace('_', ' ');
        String lower = spaced.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return "Uniform";
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private final Map<UUID, List<SkinState>> statesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> nextIdByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentIdByPlayer = new ConcurrentHashMap<>();

    /**
     * Records a new SkinState after a skin has been applied to a player.
     *
     * @param player        the player whose skin changed
     * @param name          human label ("Regular", or a formatted uniform name, etc.)
     * @param texture       base64 texture value that was applied
     * @param signature     signature that was applied
     * @param characterUUID the character this skin belongs to
     * @param textureUrl    the real texture URL for this skin
     * @param isUniform     if true, proxy values are carried from the previous state
     * @return the newly created SkinState
     */
    public SkinState record(Player player, String name, String texture, String signature,
                            String characterUUID, String textureUrl, boolean isUniform) {
        UUID uuid = player.getUniqueId();
        List<SkinState> states = statesByPlayer.computeIfAbsent(uuid, _k -> new ArrayList<>());
        int id = nextIdByPlayer.merge(uuid, 2, (old, _v) -> old + 1);

        SkinState previous = current(uuid);
        Integer previousId = previous != null ? previous.id() : null;

        String proxyTextureUrl;
        String proxyTexture;
        String proxySignature;
        if (isUniform && previous != null) {
            proxyTextureUrl = previous.proxyTextureUrl();
            proxyTexture = previous.proxyTexture();
            proxySignature = previous.proxySignature();
        } else {
            proxyTextureUrl = textureUrl;
            proxyTexture = texture;
            proxySignature = signature;
        }

        SkinState state = new SkinState(id, name, texture, signature, characterUUID, proxyTextureUrl, proxyTexture, proxySignature, previousId, null);
        states.add(state);
        currentIdByPlayer.put(uuid, id);
        return state;
    }

    /**
     * Sets an existing state as the player's current state without creating a new one.
     * Used by /skin state to switch back to a previous state.
     */
    public void setCurrent(UUID playerUUID, int stateId) {
        currentIdByPlayer.put(playerUUID, stateId);
    }

    @Nullable
    public SkinState get(UUID playerUUID, int stateId) {
        List<SkinState> states = statesByPlayer.get(playerUUID);
        if (states == null) return null;
        for (SkinState s : states) {
            if (s.id() == stateId) return s;
        }
        return null;
    }

    /**
     * Returns the state the player is currently on.
     */
    @Nullable
    public SkinState current(UUID playerUUID) {
        Integer id = currentIdByPlayer.get(playerUUID);
        if (id == null) return null;
        return get(playerUUID, id);
    }

    /**
     * Returns the most recent state for a specific character, scanning backwards.
     */
    @Nullable
    public SkinState latestForCharacter(UUID playerUUID, String characterUUID) {
        List<SkinState> states = statesByPlayer.get(playerUUID);
        if (states == null) return null;
        for (int i = states.size() - 1; i >= 0; i--) {
            SkinState s = states.get(i);
            if (s.characterUUID().equals(characterUUID)) return s;
        }
        return null;
    }

    public void clearPlayer(UUID playerUUID) {
        statesByPlayer.remove(playerUUID);
        nextIdByPlayer.remove(playerUUID);
        currentIdByPlayer.remove(playerUUID);
    }

    /**
     * Builds the clickable "Your skin has been updated: [Undo] [Re-apply]" message.
     */
    public static void sendSkinUpdatedMessage(Player player, SkinState current) {
        Component msg = ChatUtility.convertToComponent("&aYour skin has been updated: ");

        if (current.previousId() != null) {
            Component undo = ChatUtility.convertToComponent("&c[Undo]")
                    .clickEvent(ClickEvent.runCommand("/skin state " + current.previousId()))
                    .hoverEvent(HoverEvent.showText(ChatUtility.convertToComponent("&eRevert to: &b" + getPreviousName(player, current))));
            msg = msg.append(undo).append(Component.text(" "));
        }

        Component reapply = ChatUtility.convertToComponent("&b[Re-apply]")
                .clickEvent(ClickEvent.runCommand("/skin state " + current.id()))
                .hoverEvent(HoverEvent.showText(ChatUtility.convertToComponent("&eRe-apply: &b" + current.name())));
        msg = msg.append(reapply);

        player.sendMessage(msg);
    }

    private static String getPreviousName(Player player, SkinState current) {
        Integer prevId = current.previousId();
        if (prevId == null) return "Unknown";
        SkinState prev = getInstance().get(player.getUniqueId(), prevId);
        return prev != null ? prev.name() : "Unknown";
    }

    private static SkinStateManager getInstance() {
        return net.sneakycharactermanager.paper.SneakyCharacterManager.getInstance().skinStateManager;
    }
}
