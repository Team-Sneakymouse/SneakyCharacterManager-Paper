package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

public class NicknameEntity {

    private TextDisplay mounted;
    private final ServerPlayer nmsPlayer;

    public NicknameEntity(Player player) {
        nmsPlayer = ((CraftPlayer)player).getHandle();

        mounted = player.getWorld().spawn(player.getLocation(), TextDisplay.class);

        mounted.setBillboard(Display.Billboard.CENTER);
        mounted.setLineWidth(150);
        mounted.setSeeThrough(false);
        mounted.setDefaultBackground(false);
        mounted.setShadowed(true);

        mounted.setBrightness(new Display.Brightness(15, 15));
        mounted.setTransformation(new Transformation(new Vector3f(0F,0.4F,0F), new AxisAngle4f(), new Vector3f(1), new AxisAngle4f()));

        player.addPassenger(mounted);
    }

    public void setName(Component nickname) {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, nmsPlayer.level());

        temp.setText(PaperAdventure.asVanilla(nickname));

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(c.getId(),
                Objects.requireNonNull(temp.getEntityData().getNonDefaultValues()));

        for(Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().toString().equals(nmsPlayer.getStringUUID())) continue;
            ((CraftPlayer)target).getHandle().connection.send(dataPacket);
        }

        temp.remove(RemovalReason.DISCARDED);
    }

    public void setLocalizedName(Component name, Player requester) {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, nmsPlayer.level());

        temp.setText(PaperAdventure.asVanilla(name));

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(c.getId(),
                Objects.requireNonNull(temp.getEntityData().getNonDefaultValues()));

        ((CraftPlayer)requester).getHandle().connection.send(dataPacket);

        temp.remove(RemovalReason.DISCARDED);
    }

    public void destroy() {
        mounted.remove();
    }

}
