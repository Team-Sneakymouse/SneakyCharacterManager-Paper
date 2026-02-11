package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.sneakycharactermanager.paper.util.ChatUtility;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class NicknameEntity {

    private TextDisplay mounted;
    private final Player player;

    private ClientboundSetEntityDataPacket packetOff;
    private ClientboundSetEntityDataPacket packetCharacter;
    private ClientboundSetEntityDataPacket packetOn;
    private ClientboundSetEntityDataPacket packetCharacterTalking;
    private ClientboundSetEntityDataPacket packetOnTalking;
    private boolean talking = false;
    private static final int COLOR_TALKING = Color.fromARGB(160, 255, 220, 100).asARGB();
    private static final int COLOR_DEFAULT_BACKGROUND = 956301312;

    public NicknameEntity(Player player) {
        this.player = player;
        Location loc = player.getLocation().clone();
        loc.setPitch(0);
        mounted = player.getWorld().spawn(loc, TextDisplay.class);

        mounted.setBillboard(Display.Billboard.CENTER);
        mounted.setLineWidth(250);
        mounted.setShadowed(true);
        mounted.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        mounted.setBrightness(new Display.Brightness(15, 15));

        mounted.addScoreboardTag("NicknameEntity");

        player.addPassenger(mounted);

        packetOff = makePacket(Component.text(" "), 0.01F, 0);
    }

    public void updatePackets(String name) {
        packetCharacter = makePacket(
            ChatUtility.convertToComponent(name),
            0.4F,
            COLOR_DEFAULT_BACKGROUND);
        packetOn = makePacket(
            ChatUtility.convertToComponent("<white>" + name + "<newline><gray>[" + player.getName() + "]"),
            0.2F,
            COLOR_DEFAULT_BACKGROUND);

        // Talking variants: glow + warm background + speaker icon
        String talkingName = name + " \uD83D\uDD0A";
        packetCharacterTalking = makePacket(
            ChatUtility.convertToComponent(talkingName),
            0.4F,
            COLOR_TALKING);
        packetOnTalking = makePacket(
            ChatUtility.convertToComponent("<white>" + talkingName + "<newline><gold>[" + player.getName() + "]"),
            0.2F,
            COLOR_TALKING);
    }

    private ClientboundSetEntityDataPacket makePacket(Component name, float height, int backgroundColor) {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftPlayer) player).getHandle().level());

        temp.setText(PaperAdventure.asVanilla(name));
        temp.setTransformation(new com.mojang.math.Transformation(new Vector3f(0F,height,0F), new Quaternionf(), new Vector3f(1), new Quaternionf()));

        SynchedEntityData entityData = temp.getEntityData();
        entityData.set(net.minecraft.world.entity.Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, backgroundColor);

        List<DataValue<?>> nonDefault = new ArrayList<>();

        if (entityData.getNonDefaultValues() != null) {
            for (DataValue<?> dataValue : entityData.getNonDefaultValues()) {
                nonDefault.add(new DataValue(dataValue.id(), dataValue.serializer(), dataValue.value()));
            }
        }

        temp.remove(RemovalReason.DISCARDED);

        return new ClientboundSetEntityDataPacket(c.getId(), Objects.requireNonNull(nonDefault));
    }

    public void sendOff(Player requester) {
        send(packetOff, requester);
    }

    public void sendCharacter(Player requester) {
        send(talking ? packetCharacterTalking : packetCharacter, requester);
    }

    public void sendOn(Player requester) {
        send(talking ? packetOnTalking : packetOn, requester);
    }

    private void send(ClientboundSetEntityDataPacket packet, Player requester) {
        if (packet == null) return;
        if (!SneakyCharacterManager.getInstance().getConfig().getBoolean("see-own-nameplate", false)
                && requester.getUniqueId().equals(player.getUniqueId())) return;
        ((CraftPlayer)requester).getHandle().connection.send(packet);
    }

    public void setTalking(boolean talking) {
        this.talking = talking;
        var mgr = SneakyCharacterManager.getInstance().nametagManager;
        Nickname ownerNickname = mgr.getNickname(this.player);
        for (Player tracker : this.player.getTrackedBy()) {
            mgr.refreshNickname(ownerNickname, tracker);
        }
    }

    public void destroy() {
        mounted.remove();
    }

}
