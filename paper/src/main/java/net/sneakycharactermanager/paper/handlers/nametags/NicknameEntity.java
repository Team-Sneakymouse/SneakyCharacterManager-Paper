package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
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
import net.minecraft.world.entity.EntityTypes;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class NicknameEntity {

    /** Scoreboard tag carried by every SCM nameplate display. Not an API. */
    public static final String SCOREBOARD_TAG = "NicknameEntity";

    private final TextDisplay mounted;
    private final Player player;

    private ClientboundSetEntityDataPacket packetOff;
    private ClientboundSetEntityDataPacket packetCharacter;
    private ClientboundSetEntityDataPacket packetOn;
    private ClientboundSetEntityDataPacket packetCharacterTalking;
    private ClientboundSetEntityDataPacket packetOnTalking;
    private ClientboundSetEntityDataPacket packetHiddenOn;
    private ClientboundSetEntityDataPacket packetHiddenOnTalking;
    private boolean talking = false;
    private static final int COLOR_TALKING = Color.fromARGB(160, 255, 220, 100).asARGB();
    private static final int COLOR_DEFAULT_BACKGROUND = 956301312;

    public NicknameEntity(Player player) {
        this.player = player;
        Location loc = player.getLocation().clone();
        loc.setPitch(0);
        // Configured before it is added to the world, so a failing setter never leaves a stray display behind.
        mounted = player.getWorld().spawn(loc, TextDisplay.class, NicknameEntity::configure);
        try {
            player.addPassenger(mounted);

            packetOff = makePacket(Component.text(" "), 0.01F, 0);

            packetHiddenOn = makePacket(
                TextUtility.INSTANCE.convertToComponent("<gray>[ Hidden ]<newline><gray>[" + player.getName() + "]"),
                0.2F,
                COLOR_DEFAULT_BACKGROUND);
            packetHiddenOnTalking = makePacket(
                TextUtility.INSTANCE.convertToComponent("<gray>[ Hidden ] \uD83D\uDD0A<newline><gold>[" + player.getName() + "]"),
                0.2F,
                COLOR_TALKING);
        } catch (RuntimeException | Error failure) {
            try {
                mounted.remove();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static void configure(TextDisplay display) {
        // Never saved: a plate carried by a non-player anchor would otherwise be written into chunk data on unload.
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setLineWidth(250);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setBrightness(new Display.Brightness(15, 15));
        display.addScoreboardTag(SCOREBOARD_TAG);
    }

    public void updatePackets(String name) {
        updatePackets(name, null);
    }

    public void updatePackets(String name, String title) {
        boolean hasTitle = title != null && !title.isBlank();
        if (hasTitle) {
            packetCharacter = makePacket(
                TextUtility.INSTANCE.convertToComponent("<white>" + name + "<newline>" + titleLine(title)),
                0.2F,
                COLOR_DEFAULT_BACKGROUND);
        } else {
            packetCharacter = makePacket(
                TextUtility.INSTANCE.convertToComponent(name),
                0.4F,
                COLOR_DEFAULT_BACKGROUND);
        }
        packetOn = makePacket(
            TextUtility.INSTANCE.convertToComponent("<white>" + name + "<newline><gray>[" + player.getName() + "]"),
            0.2F,
            COLOR_DEFAULT_BACKGROUND);

        // Talking variants: glow + warm background + speaker icon
        String talkingName = name + " \uD83D\uDD0A";
        if (hasTitle) {
            packetCharacterTalking = makePacket(
                TextUtility.INSTANCE.convertToComponent("<white>" + talkingName + "<newline>" + titleLine(title)),
                0.2F,
                COLOR_TALKING);
        } else {
            packetCharacterTalking = makePacket(
                TextUtility.INSTANCE.convertToComponent(talkingName),
                0.4F,
                COLOR_TALKING);
        }
        packetOnTalking = makePacket(
            TextUtility.INSTANCE.convertToComponent("<white>" + talkingName + "<newline><gold>[" + player.getName() + "]"),
            0.2F,
            COLOR_TALKING);
    }

    private static String titleLine(String title) {
        String prefix = SneakyCharacterManager.getInstance().getConfig().getString("titlePrefix", "<gray>");
        if (prefix == null || prefix.isEmpty()) {
            prefix = "<gray>";
        }
        return prefix + title;
    }

    private ClientboundSetEntityDataPacket makePacket(Component name, float height, int backgroundColor) {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityTypes.TEXT_DISPLAY, ((CraftPlayer) player).getHandle().level());

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

    public void sendHiddenOn(Player requester) {
        send(talking ? packetHiddenOnTalking : packetHiddenOn, requester);
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
        if (ownerNickname == null) return;
        // The plate may ride another entity, so its own trackers are the viewers who need the update.
        for (Player tracker : this.mounted.getTrackedBy()) {
            mgr.refreshNickname(ownerNickname, tracker);
        }
        if (SneakyCharacterManager.getInstance().getConfig().getBoolean("see-own-nameplate", false)) {
            mgr.refreshNickname(ownerNickname, this.player);
        }
    }

    public void destroy() {
        mounted.remove();
    }

    TextDisplay display() {
        return mounted;
    }

    boolean isValid() {
        return mounted.isValid();
    }

    /** Mounts the plate on {@code vehicle}; true when it rides that vehicle afterwards. */
    boolean mountOn(Entity vehicle) {
        if (vehicle.equals(mounted.getVehicle())) return true;
        vehicle.addPassenger(mounted);
        return vehicle.equals(mounted.getVehicle());
    }

}
