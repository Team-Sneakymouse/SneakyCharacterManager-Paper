package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;

public class NicknameEntity {

    private Display.TextDisplay mounted;
    private final ServerPlayer nmsPlayer;

    public NicknameEntity(Player player) {
        nmsPlayer = ((CraftPlayer)player).getHandle();
        mounted = new Display.TextDisplay(EntityType.TEXT_DISPLAY, nmsPlayer.level());

        mounted.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        byte flagBytes = mounted.getFlags();
        flagBytes = (byte) (flagBytes | 2);
        flagBytes = (byte) (flagBytes | 4);

        mounted.setFlags(flagBytes);
        mounted.setBrightnessOverride(new Brightness(15, 15));
        mounted.setTransformation(new Transformation(new Vector3f(0F, 0.4F, 0F),
                new Quaternionf(), null, null));

        player.addPassenger(mounted.getBukkitEntity());
        for(Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().toString().equals(player.getUniqueId().toString())) continue;
            spawn(target);
        }
    }

    public void setName(Component nickname) {
        mounted.setText(PaperAdventure.asVanilla(nickname));
        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(mounted.getId(),
                Objects.requireNonNull(mounted.getEntityData().getNonDefaultValues()));

        for(Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().toString().equals(nmsPlayer.getStringUUID())) continue;
            ((CraftPlayer)target).getHandle().connection.send(dataPacket);
        }
    }


    public void setLocalizedName(Component name, Player requester) {
        net.minecraft.network.chat.Component ogComponent = mounted.getText();
        mounted.setText(PaperAdventure.asVanilla(name));
        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(mounted.getId(),
                Objects.requireNonNull(mounted.getEntityData().getNonDefaultValues()));
        ((CraftPlayer)requester).getHandle().connection.send(dataPacket);
        mounted.setText(ogComponent);
    }

    public void destroy() {
        ClientboundRemoveEntitiesPacket removeEntitiesPacket = new ClientboundRemoveEntitiesPacket(mounted.getId());
        for(Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().toString().equals(nmsPlayer.getStringUUID())) continue;
            ((CraftPlayer)target).getHandle().connection.send(removeEntitiesPacket);
        }
    }

    public void spawn(Player player) {
        ServerPlayer nmsTarget = ((CraftPlayer)player).getHandle();
        ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(mounted);
        ClientboundSetEntityDataPacket entityDataPacket = new ClientboundSetEntityDataPacket(mounted.getId(), Objects.requireNonNull(mounted.getEntityData().getNonDefaultValues()));
        nmsTarget.connection.send(addEntityPacket);
        nmsTarget.connection.send(entityDataPacket);
    }

}
