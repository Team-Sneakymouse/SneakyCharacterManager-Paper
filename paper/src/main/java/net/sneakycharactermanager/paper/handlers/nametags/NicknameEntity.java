package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.Objects;

import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;

public class NicknameEntity {

    private TextDisplay mounted;
    private final Player player;

    private static final Component componentOff = Component.text("");
    private Component componentCharacter;
    private Component componentOn;

    public NicknameEntity(Player player) {
        this.player = player;
        mounted = player.getWorld().spawn(player.getLocation(), TextDisplay.class);

        mounted.setBillboard(Display.Billboard.CENTER);
        mounted.setLineWidth(150);
        mounted.setShadowed(true);
        mounted.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        mounted.setBrightness(new Display.Brightness(15, 15));
        mounted.setTransformation(new Transformation(new Vector3f(0F,0.4F,0F), new AxisAngle4f(), new Vector3f(1), new AxisAngle4f()));

        mounted.addScoreboardTag("NicknameEntity");

        player.addPassenger(mounted);
    }

    public void setName(Component nickname) {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();
        mounted.text(nickname);
//        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftPlayer) player).getHandle().level());
//
//        temp.setText(PaperAdventure.asVanilla(nickname));
//
//        SynchedEntityData entityData = temp.getEntityData();
//        entityData.set(net.minecraft.world.entity.Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, ((TextComponent) nickname).content().equals("") ? 0 : 956301312);
//
//        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(c.getId(),
//                Objects.requireNonNull(entityData.getNonDefaultValues()));
//
//        for(Player target : Bukkit.getOnlinePlayers()) {
//            if (target.getUniqueId().toString().equals(player.getUniqueId().toString())) continue;
//            ((CraftPlayer)target).getHandle().connection.send(dataPacket);
//        }
//
//        temp.remove(RemovalReason.DISCARDED);
        ((CraftPlayer)this.player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(c.getId()));
    }

    public void updateComponents(String name) {
        componentCharacter = ChatUtility.convertToComponent(name);
        componentOn = ChatUtility.convertToComponent(
            "<white>" + name + "<newline><gray>[" + player.getName() + "]");
    }

    public void refreshOff(Player requester) {
        setLocalizedName(componentOff, requester);
    }

    public void refreshCharacter(Player requester) {
        setLocalizedName(componentCharacter, requester);
    }

    public void refreshOn(Player requester) {
        setLocalizedName(componentOn, requester);
    }

    private void setLocalizedName(Component name, Player requester) {
        if (requester.getUniqueId().toString().equals(player.getUniqueId().toString())) return;

        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftPlayer) player).getHandle().level());

        temp.setText(PaperAdventure.asVanilla(name));

        SynchedEntityData entityData = temp.getEntityData();
        entityData.set(net.minecraft.world.entity.Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, ((TextComponent) name).content().equals("") ? 0 : 956301312);

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(c.getId(),
                Objects.requireNonNull(entityData.getNonDefaultValues()));

        ((CraftPlayer)requester).getHandle().connection.send(dataPacket);

        temp.remove(RemovalReason.DISCARDED);
    }

    public void hideFromOwner() {
        net.minecraft.world.entity.Display.TextDisplay c = (net.minecraft.world.entity.Display.TextDisplay) ((CraftEntity) mounted).getHandle();

        net.minecraft.world.entity.Display.TextDisplay temp = new net.minecraft.world.entity.Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftPlayer) player).getHandle().level());

        temp.setText(PaperAdventure.asVanilla(componentOff));

        SynchedEntityData entityData = temp.getEntityData();
        entityData.set(net.minecraft.world.entity.Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, ((TextComponent) componentOff).content().equals("") ? 0 : 956301312);

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(c.getId(),
                Objects.requireNonNull(entityData.getNonDefaultValues()));

        ((CraftPlayer)this.player).getHandle().connection.send(dataPacket);

        temp.remove(RemovalReason.DISCARDED);
    }

    public void destroy() {
        mounted.remove();
    }

}
